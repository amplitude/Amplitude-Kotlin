package com.amplitude.core.events

import com.amplitude.core.Constants

open class GroupIdentifyEvent : BaseEvent() {
    override var eventType = Constants.GROUP_IDENTIFY_EVENT

    override fun isValid(): Boolean {
        return groups != null && groupProperties != null
    }
}
