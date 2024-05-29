package com.amplitude.android.managers

import com.amplitude.core.Storage

class EventIdManager(private val storage: Storage) {
    private var lastEventId: Long = 0
    private var lastPersistedEventId: Long = 0;


    init {
        lastPersistedEventId = getLastEventIdFromStorage()
        lastEventId = lastPersistedEventId;
    }

    internal fun getNextEventId(): Long {
        lastEventId++;
        return lastEventId;
    }

    private fun getLastEventIdFromStorage(): Long {
        return storage.read(Storage.Constants.LAST_EVENT_ID)?.toLongOrNull() ?: 0;
    }

    internal suspend fun persistLastEventId() {
        storage.write(Storage.Constants.LAST_EVENT_ID, lastEventId.toString())
        lastPersistedEventId = lastEventId;
    }

}