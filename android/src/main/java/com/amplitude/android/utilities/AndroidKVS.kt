package com.amplitude.android.utilities

import android.content.SharedPreferences
import com.amplitude.id.utilities.KeyValueStore

class AndroidKVS(private val sharedPreferences: SharedPreferences) : KeyValueStore {
    override fun getLong(key: String, defaultVal: Long): Long {
        return sharedPreferences.getLong(key, defaultVal)
    }

    override fun putLong(key: String, value: Long): Boolean {
        return sharedPreferences.edit().putLong(key, value).commit()
    }
}
