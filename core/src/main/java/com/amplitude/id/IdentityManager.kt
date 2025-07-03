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
    var userId: String? = null,
    var deviceId: String? = null,
    var userProperties: Map<String, Any> = emptyMap(),
) {
    fun setUserId(userId: String?) {
        this.userId = userId
    }

    fun setDeviceId(deviceId: String?) {
        this.deviceId = deviceId
    }

    fun setUserProperties(properties: Map<String, Any>?): Boolean {
        val oldProperties = this.userProperties
        this.userProperties = userProperties.applyUserProperties(properties)
        return oldProperties != this.userProperties
    }

    fun clearUserProperties() {
        this.userProperties = emptyMap()
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
        val editor = IdentityEditor(
            userId = currentIdentity.userId,
            deviceId = currentIdentity.deviceId,
            userProperties = currentIdentity.userProperties
        )

        editor.block()

        setIdentity(
            newIdentity = Identity(
                userId = editor.userId,
                deviceId = editor.deviceId,
                userProperties = editor.userProperties
            )
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
