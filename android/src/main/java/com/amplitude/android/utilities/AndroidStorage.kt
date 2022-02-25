package com.amplitude.android.utilities

import com.amplitude.core.Amplitude
import com.amplitude.core.Storage
import com.amplitude.core.StorageProvider
import com.amplitude.core.events.BaseEvent

class AndroidStorage(
    val amplitude: Amplitude
) : Storage {
    override fun write(event: BaseEvent) {
        TODO("Not yet implemented")
    }

    override fun rollover() {
        TODO("Not yet implemented")
    }

    override fun getEvents(): List<String> {
        TODO("Not yet implemented")
    }
}

class AndroidStorageProvider: StorageProvider {
    override fun getStorage(amplitude: Amplitude): Storage {
        return AndroidStorage(amplitude)
    }
}
