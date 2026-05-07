package carelog.carelog.relation.web.dto

import carelog.carelog.relation.domain.Relation
import carelog.carelog.relation.domain.RelationStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

data class RelationResponse(
    @Schema(description = "관계 공개 ID") val publicId: UUID,
    @Schema(description = "매니저 공개 ID") val managerPublicId: UUID,
    @Schema(description = "고객 공개 ID") val customerPublicId: UUID,
    @Schema(description = "고객 이름") val customerName: String,
    @Schema(description = "관계 상태") val status: RelationStatus,
) {
    companion object {
        fun from(relation: Relation) = RelationResponse(
            publicId = relation.publicId,
            managerPublicId = relation.manager.publicId,
            customerPublicId = relation.customer.publicId,
            customerName = relation.customer.name,
            status = relation.status,
        )
    }
}
