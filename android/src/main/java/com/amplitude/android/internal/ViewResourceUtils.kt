package com.amplitude.android.internal

import android.content.res.Resources
import android.view.View

internal object ViewResourceUtils {
    /**
     * Retrieves the human-readable view id based on `view.getContext().getResources()`, falls
     * back to a hexadecimal id representation in case the view id is not available in the resources.
     */
    val View.resourceIdWithFallback
        get() =
            runCatching { resourceId }.getOrElse { _ ->
                when {
                    id == View.NO_ID -> null
                    else -> "0x${id.toString(16)}"
                }
            }

    /**
     * Retrieves the human-readable view id based on `view.getContext().getResources()`.
     *
     * @throws Resources.NotFoundException in case the view id was not found
     */
    @get:Throws(Resources.NotFoundException::class)
    val View.resourceId: String
        get() =
            if (id == View.NO_ID || isIdGenerated) {
                throw Resources.NotFoundException()
            } else {
                context.resources?.getResourceEntryName(id) ?: ""
            }

    private val View.isIdGenerated
        get() = (id and -0x1000000) == 0 && (id and 0x00FFFFFF) != 0
}
