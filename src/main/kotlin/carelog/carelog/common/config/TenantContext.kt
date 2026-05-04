package carelog.carelog.common.config

import java.util.UUID

object TenantContext {
    private val organizationId = ThreadLocal<UUID?>()

    fun set(id: UUID?) = organizationId.set(id)
    fun get(): UUID? = organizationId.get()
    fun clear() = organizationId.remove()
}