package com.amplitude.android

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.Modifier
import com.amplitude.android.internal.compose.AmpFrustrationIgnoreElement

/**
 * Utility functions for configuring frustration analytics behavior.
 *
 * # Frustration Analytics Ignore Guide
 *
 * Exclude specific UI elements from frustration analytics detection (rage clicks and dead clicks).
 *
 * ## When to Use
 *
 * Use ignore functionality for:
 * - **Navigation elements**: Back buttons, close buttons, drawer toggles
 * - **Multi-click elements**: Increment/decrement buttons, like/favorite buttons
 * - **Loading indicators**: Progress bars, spinners, loading buttons
 * - **Decorative elements**: Non-functional UI components
 *
 * ## Usage Examples
 *
 * ### Automatic XML Processing (Recommended)
 * ```kotlin
 * class YourActivity : AppCompatActivity() {
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         // Set up automatic XML attribute processing BEFORE setContentView
 *         FrustrationAnalyticsUtils.setupAutomaticXmlProcessing(this)
 *         
 *         super.onCreate(savedInstanceState)
 *         setContentView(R.layout.your_layout)
 *         // Your XML attributes will now be automatically processed!
 *     }
 * }
 * ```
 *
 * ### Android Views (XML)
 * ```xml
 * <!-- Ignore all frustration analytics -->
 * <Button
 *     app:amplitudeIgnoreFrustration="true" />
 *
 * <!-- Ignore only rage clicks -->
 * <Button
 *     app:amplitudeIgnoreRageClick="true" />
 *
 * <!-- Ignore only dead clicks -->
 * <Button
 *     app:amplitudeIgnoreDeadClick="true" />
 * ```
 *
 * ### Android Views (Programmatic)
 * ```kotlin
 * // Ignore all frustration analytics
 * val backButton = findViewById<Button>(R.id.back_button)
 * FrustrationAnalyticsUtils.ignoreFrustrationAnalytics(backButton)
 *
 * // Ignore only rage clicks (allow dead click detection)
 * val incrementButton = findViewById<Button>(R.id.increment_button)
 * FrustrationAnalyticsUtils.ignoreFrustrationAnalytics(
 *     incrementButton,
 *     rageClick = true,
 *     deadClick = false
 * )
 * ```
 *
 * ### Manual XML Processing (Alternative)
 * If you can't use automatic processing, you can manually process views:
 * ```kotlin
 * override fun onCreate(savedInstanceState: Bundle?) {
 *     super.onCreate(savedInstanceState)
 *     setContentView(R.layout.your_layout)
 *     
 *     // Process the entire layout
 *     val rootView = findViewById<ViewGroup>(android.R.id.content)
 *     FrustrationAnalyticsUtils.processXmlAttributesRecursively(rootView)
 * }
 * ```
 *
 * ### Jetpack Compose
 * ```kotlin
 * // Ignore all frustration analytics
 * Button(
 *     onClick = { finish() },
 *     modifier = Modifier.ignoreFrustrationAnalytics()
 * ) { Text("Back") }
 *
 * // Ignore only dead clicks
 * Button(
 *     onClick = { submitForm() },
 *     modifier = Modifier.ignoreFrustrationAnalytics(
 *         rageClick = false,
 *         deadClick = true
 *     )
 * ) { Text("Submit") }
 * ```
 *
 * ## Parameter Combinations
 *
 * | rageClick | deadClick | Behavior |
 * |-----------|-----------|----------|
 * | `true` (default) | `true` (default) | Ignore all frustration analytics |
 * | `true` | `false` | Ignore only rage click detection |
 * | `false` | `true` | Ignore only dead click detection |
 * | `false` | `false` | Track both (does not ignore anything) |
 *
 * @see [ignoreFrustrationAnalytics] for Android Views
 * @see [Modifier.ignoreFrustrationAnalytics] for Jetpack Compose
 * @see [setupAutomaticXmlProcessing] for automatic XML attribute processing
 *
 * **Note**: Regular interaction events are still tracked when frustration analytics are ignored.
 */
object FrustrationAnalyticsUtils {
    // Private keys for storing ignore flags using View.setTag(key, value)
    // Using hashcodes to avoid conflicts with other tag usage
    private val IGNORE_RAGE_CLICK_KEY = "amplitude_ignore_rage_click".hashCode()
    private val IGNORE_DEAD_CLICK_KEY = "amplitude_ignore_dead_click".hashCode()
    private val IGNORE_FRUSTRATION_KEY = "amplitude_ignore_frustration".hashCode()

    /**
     * Marks an Android View to be ignored for frustration analytics.
     *
     * @param view The view to mark as ignored
     * @param rageClick Whether to ignore rage click detection (default: true)
     * @param deadClick Whether to ignore dead click detection (default: true)
     * @return The same view for chaining
     */
    fun ignoreFrustrationAnalytics(
        view: View,
        rageClick: Boolean = true,
        deadClick: Boolean = true,
    ): View {
        view.setTag(IGNORE_RAGE_CLICK_KEY, rageClick)
        view.setTag(IGNORE_DEAD_CLICK_KEY, deadClick)
        view.setTag(IGNORE_FRUSTRATION_KEY, rageClick && deadClick)
        return view
    }

    /**
     * Checks if an Android View is marked to be ignored for frustration analytics.
     */
    fun isViewIgnored(view: View): Boolean {
        val ignoreAll = view.getTag(IGNORE_FRUSTRATION_KEY) as? Boolean ?: false
        return ignoreAll
    }

    /**
     * Checks if an Android View is marked to be ignored for rage click detection.
     */
    fun isRageClickIgnored(view: View): Boolean {
        val ignoreAll = view.getTag(IGNORE_FRUSTRATION_KEY) as? Boolean ?: false
        val ignoreRage = view.getTag(IGNORE_RAGE_CLICK_KEY) as? Boolean ?: false
        return ignoreAll || ignoreRage
    }

    /**
     * Checks if an Android View is marked to be ignored for dead click detection.
     */
    fun isDeadClickIgnored(view: View): Boolean {
        val ignoreAll = view.getTag(IGNORE_FRUSTRATION_KEY) as? Boolean ?: false
        val ignoreDead = view.getTag(IGNORE_DEAD_CLICK_KEY) as? Boolean ?: false
        return ignoreAll || ignoreDead
    }

    /**
     * Removes the ignore marker from an Android View.
     */
    fun unignoreView(view: View): View {
        view.setTag(IGNORE_RAGE_CLICK_KEY, null)
        view.setTag(IGNORE_DEAD_CLICK_KEY, null)
        view.setTag(IGNORE_FRUSTRATION_KEY, null)
        return view
    }

    /**
     * Processes XML attributes for frustration analytics and sets the corresponding programmatic flags.
     * This method should be called after view inflation to ensure XML attributes are properly processed.
     * 
     * @param view The view to process
     * @param attributeSet The AttributeSet from view inflation (if available)
     * @return The same view for chaining
     */
    fun processXmlAttributes(view: View, attributeSet: android.util.AttributeSet? = null): View {
        if (attributeSet != null) {
            try {
                val context = view.context
                val typedArray = context.obtainStyledAttributes(
                    attributeSet,
                    com.amplitude.android.R.styleable.AmplitudeFrustrationAnalytics
                )

                val ignoreRageClick = typedArray.getBoolean(
                    com.amplitude.android.R.styleable.AmplitudeFrustrationAnalytics_amplitudeIgnoreRageClick,
                    false
                )
                val ignoreDeadClick = typedArray.getBoolean(
                    com.amplitude.android.R.styleable.AmplitudeFrustrationAnalytics_amplitudeIgnoreDeadClick,
                    false
                )
                val ignoreFrustration = typedArray.getBoolean(
                    com.amplitude.android.R.styleable.AmplitudeFrustrationAnalytics_amplitudeIgnoreFrustration,
                    false
                )

                typedArray.recycle()

                // Set programmatic flags based on XML attributes
                if (ignoreRageClick || ignoreDeadClick || ignoreFrustration) {
                    ignoreFrustrationAnalytics(
                        view,
                        rageClick = ignoreRageClick || ignoreFrustration,
                        deadClick = ignoreDeadClick || ignoreFrustration
                    )
                }
            } catch (e: Exception) {
                // Silently ignore XML processing errors
            }
        }
        return view
    }

    /**
     * Processes all views in a ViewGroup hierarchy to handle XML attributes.
     * This is a convenience method for processing entire layouts.
     * 
     * @param viewGroup The ViewGroup to process recursively
     */
    fun processXmlAttributesRecursively(viewGroup: android.view.ViewGroup) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            processXmlAttributes(child)
            if (child is android.view.ViewGroup) {
                processXmlAttributesRecursively(child)
            }
        }
    }

    /**
     * Sets up automatic XML attribute processing for an Activity.
     * Call this in your Activity's onCreate() before setContentView() to automatically
     * process all XML attributes during view inflation.
     * 
     * @param activity The activity to set up automatic processing for
     */
    fun setupAutomaticXmlProcessing(activity: android.app.Activity) {
        val layoutInflater = activity.layoutInflater
        val existingFactory = layoutInflater.factory2 ?: layoutInflater.factory
        
        layoutInflater.factory2 = AmplitudeFrustrationAttributeFactory(existingFactory)
    }

    /**
     * LayoutInflater.Factory2 that automatically processes Amplitude frustration analytics
     * XML attributes during view inflation.
     */
    private class AmplitudeFrustrationAttributeFactory(
        private val delegate: LayoutInflater.Factory2?
    ) : LayoutInflater.Factory2 {
        
        constructor(factory: LayoutInflater.Factory?) : this(
            if (factory is LayoutInflater.Factory2) factory else null
        )

        override fun onCreateView(
            parent: View?,
            name: String,
            context: Context,
            attrs: AttributeSet
        ): View? {
            // First, let the delegate (or system) create the view
            val view = delegate?.onCreateView(parent, name, context, attrs)
                ?: onCreateView(name, context, attrs)
            
            // Process our custom attributes if view was created
            view?.let { processXmlAttributes(it, attrs) }
            
            return view
        }

        override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? {
            return delegate?.onCreateView(name, context, attrs)
                ?: createViewFromTag(context, name, attrs)
        }

        private fun createViewFromTag(context: Context, name: String, attrs: AttributeSet): View? {
            return try {
                when {
                    name.contains('.') -> {
                        // Fully qualified class name
                        val clazz = Class.forName(name)
                        val constructor = clazz.getConstructor(Context::class.java, AttributeSet::class.java)
                        constructor.newInstance(context, attrs) as View
                    }
                    else -> {
                        // Try standard Android view packages
                        val packages = arrayOf("android.widget.", "android.view.", "android.webkit.")
                        for (pkg in packages) {
                            try {
                                val clazz = Class.forName(pkg + name)
                                val constructor = clazz.getConstructor(Context::class.java, AttributeSet::class.java)
                                return constructor.newInstance(context, attrs) as View
                            } catch (e: ClassNotFoundException) {
                                // Continue to next package
                            }
                        }
                        null
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Jetpack Compose modifier for ignoring frustration analytics.
 *
 * @param rageClick Whether to ignore rage click detection (default: true)
 * @param deadClick Whether to ignore dead click detection (default: true)
 */
fun Modifier.ignoreFrustrationAnalytics(
    rageClick: Boolean = true,
    deadClick: Boolean = true,
): Modifier {
    return if (!rageClick && !deadClick) {
        // Don't ignore anything, return unmodified
        this
    } else {
        this.then(AmpFrustrationIgnoreElement(rageClick, deadClick))
    }
}
