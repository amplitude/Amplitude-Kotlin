package com.amplitude.android

class AutocaptureOptions
@JvmOverloads
constructor(
    var sessions: Boolean = true,
    var appLifecycles: Boolean = false,
    var deepLinks: Boolean = false,
    var screenViews: Boolean = false,
    var elementInteractions: Boolean = false,
)