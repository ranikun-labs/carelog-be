package carelog.carelog.relation.web.dto;

import carelog.carelog.relation.domain.Relation;
import carelog.carelog.relation.domain.RelationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record RelationResponse(
        @Schema(description = "관계 공개 ID") UUID publicId,
        @Schema(description = "매니저 공개 ID") UUID managerPublicId,
        @Schema(description = "고객 공개 ID") UUID customerPublicId,
        @Schema(description = "고객 이름") String customerName,
        @Schema(description = "관계 상태") RelationStatus status
) {
    public static RelationResponse from(Relation relation) {
        return new RelationResponse(
                relation.getPublicId(),
                relation.getManager().getPublicId(),
                relation.getCustomer().getPublicId(),
                relation.getCustomer().getName(),
                relation.getStatus()
        );
    }
}
