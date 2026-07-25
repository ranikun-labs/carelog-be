package carelog.carelog.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.util.UUID;

@Getter
@MappedSuperclass
@FilterDef(
        name = "organizationFilter",
        parameters = @ParamDef(name = "organizationId", type = UUID.class)
)
@Filter(name = "organizationFilter", condition = "organization_id = :organizationId")
public abstract class TenantBaseEntity extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    public void assignOrganization(UUID organizationId) {
        this.organizationId = organizationId;
    }
}