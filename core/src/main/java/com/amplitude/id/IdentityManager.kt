package com.amplitude.id
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
    interface Editor {
        fun setUserId(userId: String?): Editor

        fun setDeviceId(deviceId: String?): Editor

        fun setUserProperties(userProperties: Map<String, Any>): Editor

        fun commit()
    }

    fun editIdentity(): Editor

    fun setIdentity(newIdentity: Identity)

    fun getIdentity(): Identity

    fun addIdentityListener(listener: IdentityListener)

    fun removeIdentityListener(listener: IdentityListener)
}

class IdentityEditor(
    var userId: String? = null,
    var deviceId: String? = null,
    var userProperties: Map<String, Any> = emptyMap(),
)

internal class IdentityManagerImpl(
    private val identityStorage: IdentityStorage,
) : IdentityManager {
    private val identityLock = ReentrantReadWriteLock(true)
    private var identity: Identity = identityStorage.load()

    private val listeners: CopyOnWriteArrayList<IdentityListener> = CopyOnWriteArrayList()

    override fun editIdentity(): IdentityManager.Editor {
        val editor = getIdentity().run { IdentityEditor(userId, deviceId) }
        return object : IdentityManager.Editor {
            override fun setUserId(userId: String?): IdentityManager.Editor = this.apply { editor.userId = userId }

            override fun setDeviceId(deviceId: String?): IdentityManager.Editor = this.apply { editor.deviceId = deviceId }

            override fun setUserProperties(userProperties: Map<String, Any>): IdentityManager.Editor =
                this.apply { editor.userProperties = userProperties }

            override fun commit() {
                setIdentity(Identity(editor.userId, editor.deviceId, editor.userProperties))
            }
        }
    }

    override fun setIdentity(newIdentity: Identity) {
        val originalIdentity =
            identityLock.write {
                val oldIdentity = identity
                if (newIdentity != oldIdentity) {
                    this.identity = newIdentity
                }
                oldIdentity
            }
        if (newIdentity == originalIdentity) return

        if (identity.userId != originalIdentity.userId) {
            identityStorage.saveUserId(identity.userId)
        }

        if (identity.deviceId != originalIdentity.deviceId) {
            identityStorage.saveDeviceId(identity.deviceId)
        }

        for (listener in listeners) {
            listener.onIdentityChanged(identity)
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
