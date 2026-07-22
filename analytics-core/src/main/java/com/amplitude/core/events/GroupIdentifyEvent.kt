package com.amplitude.core.events

import com.amplitude.core.Constants

public open class GroupIdentifyEvent : BaseEvent() {
    override var eventType: String = Constants.GROUP_IDENTIFY_EVENT

    override fun isValid(): Boolean {
        return groups != null && groupProperties != null
    }
}
