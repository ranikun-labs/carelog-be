package carelog.carelog.common.domain

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import org.hibernate.annotations.Filter
import org.hibernate.annotations.FilterDef
import org.hibernate.annotations.ParamDef
import java.util.UUID

@MappedSuperclass
@FilterDef(
    name = "organizationFilter",
    parameters = [ParamDef(name = "organizationId", type = UUID::class)]
)
@Filter(name = "organizationFilter", condition = "organization_id = :organizationId")
abstract class TenantBaseEntity : BaseEntity() {

    @Column(name = "organization_id", nullable = false, updatable = false)
    var organizationId: UUID? = null
        protected set

    fun assignOrganization(organizationId: UUID) {
        this.organizationId = organizationId
    }
}