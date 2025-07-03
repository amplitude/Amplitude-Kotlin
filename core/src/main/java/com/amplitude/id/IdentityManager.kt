package com.amplitude.id
import com.amplitude.core.events.applyUserProperties
import com.amplitude.core.platform.plugins.AnalyticsIdentity
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

interface IdentityListener {
    fun onIdentityChanged(identity: Identity)
}

data class Identity(
    override val userId: String? = null,
    override val deviceId: String? = null,
    override val userProperties: Map<String, Any> = emptyMap(),
) : AnalyticsIdentity

/**
 * Identity Manager manages the identity for certain instance.
 *
 */
interface IdentityManager {
    fun editIdentity(block: IdentityEditor.() -> Unit)

    fun setIdentity(newIdentity: Identity)

    fun getIdentity(): Identity

    fun addIdentityListener(listener: IdentityListener)

    fun removeIdentityListener(listener: IdentityListener)
}

class IdentityEditor(
    var _userId: String? = null,
    var _deviceId: String? = null,
    var _userProperties: Map<String, Any> = emptyMap(),
) {
    fun setUserId(userId: String?) {
        _userId = userId
    }

    fun setDeviceId(deviceId: String?) {
        _deviceId = deviceId
    }

    fun setUserProperties(properties: Map<String, Any>?): Boolean {
        val oldProperties = _userProperties
        _userProperties = _userProperties.applyUserProperties(properties)
        return oldProperties != _userProperties
    }

    fun clearUserProperties() {
        _userProperties = emptyMap()
    }
}

internal class IdentityManagerImpl(
    private val identityStorage: IdentityStorage,
) : IdentityManager {
    private val identityLock = ReentrantReadWriteLock(true)
    private var identity: Identity = identityStorage.load()

    private val listeners: CopyOnWriteArrayList<IdentityListener> = CopyOnWriteArrayList()

    override fun editIdentity(block: IdentityEditor.() -> Unit) {
        val currentIdentity = getIdentity()
        val editor =
            IdentityEditor(
                _userId = currentIdentity.userId,
                _deviceId = currentIdentity.deviceId,
                _userProperties = currentIdentity.userProperties,
            )

        editor.block()

        setIdentity(
            newIdentity =
                Identity(
                    userId = editor._userId,
                    deviceId = editor._deviceId,
                    userProperties = editor._userProperties,
                ),
        )
    }

    override fun setIdentity(newIdentity: Identity) {
        val originalIdentity =
            identityLock.write {
                val oldIdentity = identity
                if (newIdentity != oldIdentity) {
                    this.identity = newIdentity

                    if (newIdentity.userId != oldIdentity.userId) {
                        identityStorage.saveUserId(newIdentity.userId)
                    }

                    if (newIdentity.deviceId != oldIdentity.deviceId) {
                        identityStorage.saveDeviceId(identity.deviceId)
                    }
                }
                oldIdentity
            }

        if (newIdentity == originalIdentity) return

        for (listener in listeners) {
            listener.onIdentityChanged(
                identity = identity,
            )
        }
    }

    override fun getIdentity(): Identity {
        return identityLock.read { this.identity }
    }

    override fun addIdentityListener(listener: IdentityListener) {
        listeners.add(listener)
    }

    override fun removeIdentityListener(listener: IdentityListener) {
        listeners.remove(listener)
    }
}
