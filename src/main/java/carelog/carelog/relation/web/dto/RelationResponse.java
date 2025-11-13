package carelog.carelog.relation.web.dto;

import carelog.carelog.relation.domain.*;
import lombok.*;

@Getter
public class RelationResponse {
    private final Long id;
    private final Long managerId;
    private final Long customerId;
    private final RelationStatus status;

    private RelationResponse(Long id, Long managerId, Long customerId, RelationStatus status) {
        this.id = id;
        this.managerId = managerId;
        this.customerId = customerId;
        this.status = status;
    }

    public static RelationResponse from(Relation relation) {
        return new RelationResponse(
                relation.getId(),
                relation.getManager().getId(),
                relation.getCustomer().getId(),
                relation.getStatus()
        );
    }
}
