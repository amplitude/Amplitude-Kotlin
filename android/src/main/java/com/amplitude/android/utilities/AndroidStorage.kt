package com.amplitude.android.utilities

import com.amplitude.core.Amplitude
import com.amplitude.core.Storage
import com.amplitude.core.StorageProvider
import com.amplitude.core.events.BaseEvent

class AndroidStorage(
    val amplitude: Amplitude
) : Storage {
    override suspend fun writeEvent(event: BaseEvent) {
        TODO("Not yet implemented")
    }

    override suspend fun write(key: Storage.Constants, value: String) {
        TODO("Not yet implemented")
    }

    override suspend fun rollover() {
        TODO("Not yet implemented")
    }

    override fun read(key: Storage.Constants): String? {
        TODO("Not yet implemented")
    }

    override fun readEventsContent(): List<Any> {
        TODO("Not yet implemented")
    }

    override fun getEventsString(content: Any): String {
        TODO("Not yet implemented")
    }
}

class AndroidStorageProvider : StorageProvider {
    override fun getStorage(amplitude: Amplitude): Storage {
        return AndroidStorage(amplitude)
    }
}
