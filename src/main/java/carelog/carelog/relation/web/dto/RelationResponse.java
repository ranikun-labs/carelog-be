package carelog.carelog.relation.web.dto;

import carelog.carelog.relation.domain.*;
import lombok.*;

import java.util.UUID;

@Getter
public class RelationResponse {
    private final UUID publicId;
    private final UUID managerPublicId;
    private final UUID customerPublicId;
    private final RelationStatus status;

    private RelationResponse(UUID publicId, UUID managerPublicId, UUID customerPublicId, RelationStatus status) {
        this.publicId = publicId;
        this.managerPublicId = managerPublicId;
        this.customerPublicId = customerPublicId;
        this.status = status;
    }

    public static RelationResponse from(Relation relation) {
        return new RelationResponse(
                relation.getPublicId(),
                relation.getManager().getPublicId(),
                relation.getCustomer().getPublicId(),
                relation.getStatus()
        );
    }
}
