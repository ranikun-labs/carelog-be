package carelog.carelog.relation.web

import carelog.carelog.auth.app.CustomUserDetails
import carelog.carelog.common.web.dto.response.ApiResponse
import carelog.carelog.relation.app.RelationService
import carelog.carelog.relation.domain.RelationStatus
import carelog.carelog.relation.web.dto.RelationCreateRequest
import carelog.carelog.relation.web.dto.RelationResponse
import carelog.carelog.relation.web.dto.RelationStatusUpdateRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/relations")
class RelationController(
    private val relationService: RelationService,
) {
    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    fun createRelation(
        @Valid @RequestBody request: RelationCreateRequest,
        @AuthenticationPrincipal userDetails: CustomUserDetails,
    ): ResponseEntity<ApiResponse<RelationResponse>> =
        ApiResponse.created(relationService.createRelation(request.customerPublicId, userDetails))

    @GetMapping("/{relationPublicId}")
    fun findRelationByPublicId(
        @PathVariable relationPublicId: UUID,
        @AuthenticationPrincipal userDetails: CustomUserDetails,
    ): ResponseEntity<ApiResponse<RelationResponse>> =
        ApiResponse.ok(relationService.findRelationByPublicId(relationPublicId, userDetails))

    @GetMapping("/manager/{managerPublicId}/customer/{customerPublicId}")
    fun findRelationByManagerAndCustomer(
        @PathVariable managerPublicId: UUID,
        @PathVariable customerPublicId: UUID,
        @AuthenticationPrincipal userDetails: CustomUserDetails,
    ): ResponseEntity<ApiResponse<RelationResponse>> =
        ApiResponse.ok(relationService.findRelationByManagerAndCustomer(managerPublicId, customerPublicId, userDetails))

    @GetMapping("/manager")
    fun findAllRelationByManager(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
    ): ResponseEntity<ApiResponse<List<RelationResponse>>> =
        ApiResponse.ok(relationService.findAllRelationsByManager(userDetails))

    @GetMapping("/customer/{customerPublicId}")
    fun findAllRelationByCustomer(
        @PathVariable customerPublicId: UUID,
        @AuthenticationPrincipal userDetails: CustomUserDetails,
    ): ResponseEntity<ApiResponse<List<RelationResponse>>> =
        ApiResponse.ok(relationService.findAllRelationsByCustomer(customerPublicId, userDetails))

    @PatchMapping("/{relationPublicId}/status")
    @PreAuthorize("hasRole('MANAGER')")
    fun updateRelationStatus(
        @PathVariable relationPublicId: UUID,
        @Valid @RequestBody request: RelationStatusUpdateRequest,
        @AuthenticationPrincipal userDetails: CustomUserDetails,
    ): ResponseEntity<ApiResponse<RelationResponse>> =
        ApiResponse.ok(relationService.updateRelationsStatus(relationPublicId, request.status, userDetails))

    @DeleteMapping("/{relationPublicId}")
    @PreAuthorize("hasRole('MANAGER')")
    fun deleteRelation(
        @PathVariable relationPublicId: UUID,
        @AuthenticationPrincipal userDetails: CustomUserDetails,
    ): ResponseEntity<Void> {
        relationService.deleteRelation(relationPublicId, userDetails)
        return ApiResponse.noContent()
    }
}
