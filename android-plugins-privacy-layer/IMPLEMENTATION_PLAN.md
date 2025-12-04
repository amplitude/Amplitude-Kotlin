# Privacy Layer Plugin Implementation Plan

## Overview

**Goal**: Create an Amplitude plugin that automatically detects and redacts PII data before events are sent to Amplitude servers.

**Key Components**:
- **MLKit Entity Extraction API** for detecting "tricky" PII (emails, phone numbers, addresses, credit cards, IBANs)
- **Plugin Architecture** using Amplitude's Before pipeline type
- **Configurable redaction** strategies and field scanning

---

## Architecture Design

### Plugin Type: `Plugin.Type.Before`

**Why Before?**
- Intercepts events early in the pipeline before any processing or storage
- Prevents PII from ever reaching storage or network layers
- Executes before ContextPlugin and AndroidContextPlugin add additional context

### Event Fields to Scan

From the Amplitude event structure, these fields can contain PII:

**String Fields:**
- `userId` - might be email address

**Map Fields (recursively scan):**
- `eventProperties` - custom key-value pairs (highest risk)
- `userProperties` - user profile data (high risk)
- `groupProperties` - group data
- `extra` - arbitrary data

**Skip Fields:**
- Numeric: `timestamp`, `sessionId`, `eventId`, `locationLat`, `locationLng`
- System: `library`, `platform`, `osName`, `osVersion`
- `eventType` - custom event names are set by devs
- `deviceId` - set by devs
- `ip` - set by devs
- Device identifiers: `adid`, `idfa`, `idfv`, `androidId` - set by devs
- Location: `city`, `region`, `country` - these are fine

---

## PII Detection Strategy

### MLKit Entity Extraction

Detects 11 entity types:

**PII-related:**
1. ✉️ **Email addresses** - `entity-extraction@google.com`
2. 📱 **Phone numbers** - `(555) 225-3556`
3. 🏠 **Addresses** - `350 third street, Cambridge MA`
4. 💳 **Payment cards** - `4111 1111 1111 1111`
5. 🏦 **IBAN** - `CH52 0483 0000 0000 0000 9`
6. 🔗 **URLs** - `www.google.com` (might contain PII in query params)

**Other entities:**
7. 📅 Date-Time
8. ✈️ Flight numbers
9. 📚 ISBN
10. 💵 Money/Currency
11. 📦 Tracking numbers

### Additional Regex Patterns (Optional)

For PII types not covered by MLKit:
- Social Security Numbers (SSN): `\d{3}-\d{2}-\d{4}`
- API Keys/Tokens: Long alphanumeric strings
- Custom patterns based on your domain

---

## Redaction Strategy

### Option 1: Type-Specific Replacement (Recommended)
```kotlin
"john.doe@example.com" → "[REDACTED_EMAIL]"
"(555) 123-4567" → "[REDACTED_PHONE]"
"4111 1111 1111 1111" → "[REDACTED_CARD]"
```

**Pros**: Clear what was redacted, helps debugging
**Cons**: None

### Option 2: Hash-based Redaction
```kotlin
"john.doe@example.com" → "hash:a3f5d9e2..."
```

**Pros**: Consistent identifier for same value, allows deduplication
**Cons**: Still potentially reversible with rainbow tables

### Option 3: Complete Removal
```kotlin
properties: { "email": "john@example.com", "name": "John" }
→ properties: { "name": "John" }
```

**Pros**: Most secure
**Cons**: Loses data completely, harder to debug

---

## Configuration Options

```kotlin
data class PrivacyLayerConfig(
    // Which entity types to detect and redact
    val entityTypes: Set<EntityType> = setOf(
        EntityType.EMAIL,
        EntityType.PHONE_NUMBER,
        EntityType.ADDRESS,
        EntityType.PAYMENT_CARD,
        EntityType.IBAN,
    ),

    // Which event fields to scan
    val scanFields: Set<ScanField> = setOf(
        ScanField.EVENT_TYPE,
        ScanField.EVENT_PROPERTIES,
        ScanField.USER_PROPERTIES,
        ScanField.GROUP_PROPERTIES,
        ScanField.EXTRA,
    ),

    // Redaction strategy
    val redactionStrategy: RedactionStrategy = RedactionStrategy.TYPE_SPECIFIC,

    // Whitelist patterns (don't redact these)
    val whitelist: List<Regex> = listOf(
        Regex(".*@mycompany\\.com"),  // Allow company emails
    ),

    // Additional regex patterns for custom PII
    val customPatterns: Map<String, Regex> = emptyMap(),

    // Performance: Max text length to analyze (prevent OOM)
    val maxTextLength: Int = 10_000,

    // Performance: Enable/disable MLKit (fallback to regex only)
    val useMlKit: Boolean = true,

    // Language for entity extraction
    val language: String = EntityExtractorLanguage.ENGLISH,
)
```

---

## File Structure

```
android/src/main/java/com/amplitude/android/plugins/privacy_layer/
├── PrivacyLayerPlugin.kt              # Main plugin implementation
├── PrivacyLayerConfig.kt              # Configuration data class
├── PiiDetector.kt                     # Core PII detection logic
├── MlKitPiiDetector.kt                # MLKit-based detection
├── RegexPiiDetector.kt                # Regex-based detection
├── PiiRedactor.kt                     # Redaction strategies
└── models/
    ├── DetectedPii.kt                 # Data class for detected PII
    ├── ScanField.kt                   # Enum for scannable fields
    └── RedactionStrategy.kt           # Enum for redaction types
```

---

## Implementation Steps

### Phase 1: Core Infrastructure
1. **Create plugin skeleton**
   - Implement `Plugin` interface with `Type.Before`
   - Add configuration class
   - Set up gradle module structure, it should be an add-on on top of the SDK

2. **Implement basic string scanning**
   - Recursively traverse event properties (Map<String, Any?>)
   - Extract all string values for analysis
   - Handle nested objects and arrays

### Phase 2: MLKit Integration
3. **Add MLKit dependency**
   - Add `com.google.mlkit:entity-extraction:16.0.0-beta6`
   - Implement model download logic (one-time, async)
   - Handle model download failures gracefully

4. **Implement MLKit-based detection**
   - Create `EntityExtractor` client
   - Process text through `annotate()` method
   - Map entity types to PII categories
   - Handle async nature (should we block the pipeline?)

### Phase 3: Redaction Logic
5. **Implement redaction strategies**
   - Type-specific replacement
   - Hash-based redaction (optional)
   - Field removal (optional)
   - Whitelist filtering

6. **Apply redactions to events**
   - Replace detected PII in string fields
   - Update nested properties recursively
   - Preserve event structure

### Phase 4: Performance & Polish
7. **Add regex-based fallback**
   - Implement common PII patterns (SSN, API keys)
   - Allow custom regex patterns via config
   - Use as fallback if MLKit unavailable

8. **Performance optimizations**
   - Limit text length to prevent OOM
   - Cache EntityExtractor instance
   - Consider async processing with timeout
   - Batch entity extraction calls

9. **Add comprehensive tests**
   - Unit tests with mock events containing PII
   - Integration tests with actual MLKit
   - Performance benchmarks
   - Edge cases (null values, empty strings, large objects)

### Phase 5: Documentation & Examples
10. **Create documentation**
    - README with usage examples
    - Configuration guide
    - Performance characteristics
    - Privacy compliance notes

11. **Sample app integration**
    - Add to kotlin-android-app sample
    - Demo different configuration options
    - Show before/after event comparison

---

## Important Considerations

### Performance Impact
- **MLKit model download**: ~700KB one-time download
- **Entity extraction**: May add 10-100ms per event (needs benchmarking)
- **Should we process async?**: Consider `coroutineScope` with timeout
- **Recommendation**: Make async processing optional via config

### False Positives/Negatives
- MLKit "focuses on precision over recognition" - may miss some PII
- Regex patterns may have false positives (e.g., "555-1234" might not be a real phone)
- **Recommendation**: Provide callbacks for custom validation logic

### Privacy Compliance
- This plugin helps with GDPR, CCPA, HIPAA compliance
- Still needs proper documentation and user consent flows
- **Not a replacement** for proper data handling practices

### Error Handling
- MLKit model download failures
- Entity extraction timeouts
- Malformed data (deeply nested objects)
- **Recommendation**: Fail open (send event without redaction) vs fail closed (drop event)?

---

## Quick Start (After Implementation)

```kotlin
val amplitude = Amplitude(
    apiKey = "YOUR_API_KEY",
    context = applicationContext,
)

// Add privacy layer plugin with default config
amplitude.add(
    PrivacyLayerPlugin(
        config = PrivacyLayerConfig(
            entityTypes = setOf(
                EntityType.EMAIL,
                EntityType.PHONE_NUMBER,
                EntityType.PAYMENT_CARD,
            ),
            redactionStrategy = RedactionStrategy.TYPE_SPECIFIC,
        )
    )
)

// Events will automatically have PII redacted
amplitude.track("User Signup", mapOf(
    "email" to "user@example.com",  // → [REDACTED_EMAIL]
    "name" to "John Doe",            // → "John Doe" (preserved)
    "phone" to "555-1234",           // → [REDACTED_PHONE]
))
```

---

## Success Metrics

- ✅ Detects 95%+ of common PII types (email, phone, address, cards)
- ✅ Redacts PII with <100ms latency impact per event
- ✅ Zero PII reaches Amplitude servers
- ✅ Configurable and extensible for custom PII types
- ✅ Comprehensive test coverage (>80%)

---

## References

- [MLKit Entity Extraction Overview](https://developers.google.com/ml-kit/language/entity-extraction)
- [MLKit Entity Extraction Android](https://developers.google.com/ml-kit/language/entity-extraction/android)
- [MLKit Announcement](https://developers.googleblog.com/en/announcing-the-newest-addition-to-mlkit-entity-extraction/)
- Amplitude Plugin Architecture: `core/src/main/java/com/amplitude/core/platform/Plugin.kt`
- Example Plugins: `android/src/main/java/com/amplitude/android/plugins/`
