package carelog.carelog.relation.web.dto;

import carelog.carelog.relation.domain.Relation;
import carelog.carelog.relation.domain.RelationStatus;
import lombok.Getter;

@Getter
public class RelationResponse {
    private final int id;
    private final Long managerId;
    private final Long customerId;
    private final RelationStatus status;

    private RelationResponse(int id, Long managerId, Long customerId, RelationStatus status) {
        this.id = id;
        this.managerId = mangerId;
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
