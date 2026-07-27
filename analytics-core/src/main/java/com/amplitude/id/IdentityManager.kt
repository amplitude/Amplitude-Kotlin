package com.amplitude.id

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

public interface IdentityListener {
    public fun onUserIdChange(userId: String?)

    public fun onDeviceIdChange(deviceId: String?)

    public fun onIdentityChanged(
        identity: Identity,
        updateType: IdentityUpdateType,
    )
}

public data class Identity(
    val userId: String? = null,
    val deviceId: String? = null,
)

public enum class IdentityUpdateType {
    Initialized,
    Updated,
}

/**
 * Identity Manager manages the identity for certain instance.
 *
 */
public interface IdentityManager {
    public interface Editor {
        public fun setUserId(userId: String?): Editor

        public fun setDeviceId(deviceId: String?): Editor

        public fun commit()
    }

    public fun editIdentity(): Editor

    public fun setIdentity(
        identity: Identity,
        updateType: IdentityUpdateType = IdentityUpdateType.Updated,
    )

    public fun getIdentity(): Identity

    public fun addIdentityListener(listener: IdentityListener)

    public fun removeIdentityListener(listener: IdentityListener)

    public fun isInitialized(): Boolean
}

internal class IdentityManagerImpl(
    private val identityStorage: IdentityStorage,
    persistDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
) : IdentityManager {
    private val identityStorageScope = CoroutineScope(SupervisorJob() + persistDispatcher)
    private val identityLock = ReentrantReadWriteLock(true)
    private var identity = Identity()

    private val listenersLock = Any()
    private val listeners: MutableSet<IdentityListener> = mutableSetOf()
    private var initialized: Boolean = false

    init {
        setIdentity(identityStorage.load(), IdentityUpdateType.Initialized)
    }

    override fun editIdentity(): IdentityManager.Editor {
        val originalIdentity = getIdentity()
        return object : IdentityManager.Editor {
            private var userId: String? = originalIdentity.userId
            private var deviceId: String? = originalIdentity.deviceId

            override fun setUserId(userId: String?): IdentityManager.Editor {
                this.userId = userId
                return this
            }

            override fun setDeviceId(deviceId: String?): IdentityManager.Editor {
                this.deviceId = deviceId
                return this
            }

            override fun commit() {
                val newIdentity = Identity(userId, deviceId)
                setIdentity(newIdentity)
            }
        }
    }

    override fun setIdentity(
        identity: Identity,
        updateType: IdentityUpdateType,
    ) {
        val originalIdentity = getIdentity()
        identityLock.write {
            this.identity = identity
            if (updateType == IdentityUpdateType.Initialized) {
                initialized = true
            }
        }
        if (identity != originalIdentity) {
            val safeListeners =
                synchronized(listenersLock) {
                    listeners.toSet()
                }

            if (updateType != IdentityUpdateType.Initialized) {
                val userIdChanged = identity.userId != originalIdentity.userId
                val deviceIdChanged = identity.deviceId != originalIdentity.deviceId
                persistIdentity(identity, userIdChanged, deviceIdChanged)
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

    private fun persistIdentity(
        identity: Identity,
        userIdChanged: Boolean,
        deviceIdChanged: Boolean,
    ) {
        if (!userIdChanged && !deviceIdChanged) return

        identityStorageScope.launch {
            if (userIdChanged) identityStorage.saveUserId(identity.userId)
            if (deviceIdChanged) identityStorage.saveDeviceId(identity.deviceId)
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
