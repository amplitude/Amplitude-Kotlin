package com.amplitude.core

/** Optional storage capability for persisting unfinished upload requests across process restarts. */
@RestrictedAmplitudeFeature
public interface UploadRequestStateStorage {
    /** Whether a request started without an observed response or request error. */
    public var uploadRequestPending: Boolean
}
