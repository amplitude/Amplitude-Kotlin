package com.amplitude.id

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal const val ID_OP_SET = "\$set"
internal const val ID_OP_UNSET = "\$unset"
internal const val ID_OP_CLEAR_ALL = "\$clearAll"

interface IdentityListener {

    fun onUserIdChange(userId: String?)

    fun onDeviceIdChange(deviceId: String?)

    fun onIdentityChanged(identity: Identity, updateType: IdentityUpdateType)
}

data class Identity(
    val userId: String? = null,
    val deviceId: String? = null,
    val userProperties: Map<String, Any?> = mapOf(),
)

enum class IdentityUpdateType {
    Initialized, Updated
}

interface IdentityManager {

    interface Editor {

        fun setUserId(userId: String?): Editor
        fun setDeviceId(deviceId: String?): Editor
        fun setUserProperties(userProperties: Map<String, Any?>): Editor
        fun updateUserProperties(actions: Map<String, Map<String, Any?>>): Editor
        fun commit()
    }

    fun editIdentity(): Editor
    fun setIdentity(identity: Identity, updateType: IdentityUpdateType = IdentityUpdateType.Updated)
    fun getIdentity(): Identity
    fun addIdentityListener(listener: IdentityListener)
    fun removeIdentityListener(listener: IdentityListener)
    fun isInitialized(): Boolean
}

internal class IdentityManagerImpl: IdentityManager {

    private val identityLock = ReentrantReadWriteLock(true)
    private var identity = Identity()

    private val listenersLock = Any()
    private val listeners: MutableSet<IdentityListener> = mutableSetOf()
    private var initialized: Boolean = false

    override fun editIdentity(): IdentityManager.Editor {
        val originalIdentity = getIdentity()
        return object : IdentityManager.Editor {

            private var userId: String? = originalIdentity.userId
            private var deviceId: String? = originalIdentity.deviceId
            private var userProperties: Map<String, Any?> = originalIdentity.userProperties

            override fun setUserId(userId: String?): IdentityManager.Editor {
                this.userId = userId
                return this
            }

            override fun setDeviceId(deviceId: String?): IdentityManager.Editor {
                this.deviceId = deviceId
                return this
            }

            override fun setUserProperties(userProperties: Map<String, Any?>): IdentityManager.Editor {
                this.userProperties = userProperties
                return this
            }

            override fun updateUserProperties(actions: Map<String, Map<String, Any?>>): IdentityManager.Editor {
                val actingProperties = this.userProperties.toMutableMap()
                for (actionEntry in actions.entries) {
                    val action = actionEntry.key
                    val properties = actionEntry.value
                    when (action) {
                        ID_OP_SET -> {
                            actingProperties.putAll(properties)
                        }
                        ID_OP_UNSET -> {
                            for (entry in properties.entries) {
                                actingProperties.remove(entry.key)
                            }
                        }
                        ID_OP_CLEAR_ALL -> {
                            actingProperties.clear()
                        }
                    }
                }
                this.userProperties = actingProperties
                return this
            }

            override fun commit() {
                val newIdentity = Identity(userId, deviceId, userProperties)
                setIdentity(newIdentity)
            }
        }
    }

    override fun setIdentity(identity: Identity, updateType: IdentityUpdateType) {
        val originalIdentity = getIdentity()
        identityLock.write {
            this.identity = identity
            if (updateType == IdentityUpdateType.Initialized) {
                initialized = true
            }
        }
        if (identity != originalIdentity) {
            val safeListeners = synchronized(listenersLock) {
                listeners.toSet()
            }

            for (listener in safeListeners) {
                if (identity.userId != originalIdentity.userId) {
                    listener.onUserIdChange(identity.userId)
                }
                if (identity.deviceId != originalIdentity.deviceId) {
                    listener.onDeviceIdChange(identity.deviceId)
                }
                listener.onIdentityChanged(identity, updateType)
            }
        }
    }

    override fun getIdentity(): Identity {
        return identityLock.read {
            this.identity
        }
    }

    override fun addIdentityListener(listener: IdentityListener) {
        synchronized(listenersLock) {
            listeners.add(listener)
        }
    }

    override fun removeIdentityListener(listener: IdentityListener) {
        synchronized(listenersLock) {
            listeners.remove(listener)
        }
    }

    override fun isInitialized(): Boolean {
        return initialized
    }
}
