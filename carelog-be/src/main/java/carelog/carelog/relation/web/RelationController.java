package carelog.carelog.relation.web;

import carelog.carelog.auth.app.UserPrincipal;
import carelog.carelog.common.web.dto.response.*;
import carelog.carelog.relation.app.*;
import carelog.carelog.relation.web.dto.*;
import jakarta.validation.*;
import lombok.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/relations")
@RequiredArgsConstructor
public class RelationController {

    private final RelationService relationService;

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<RelationResponse>> createRelation(
            @Valid @RequestBody RelationCreateRequest request,
            @AuthenticationPrincipal UserPrincipal userDetails
    ) {
        RelationResponse response = relationService.createRelation(request.customerPublicId(), userDetails);
        return ApiResponse.created(response);
    }

    @GetMapping("/{relationPublicId}")
    public ResponseEntity<ApiResponse<RelationResponse>> findRelationByPublicId(
            @PathVariable UUID relationPublicId,
            @AuthenticationPrincipal UserPrincipal userDetails
    ) {
        RelationResponse response = relationService.findRelationByPublicId(relationPublicId, userDetails);
        return ApiResponse.ok(response);
    }

    @GetMapping("/manager/{managerPublicId}/customer/{customerPublicId}")
    public ResponseEntity<ApiResponse<RelationResponse>> findRelationByManagerAndCustomer(
            @PathVariable UUID managerPublicId,
            @PathVariable UUID customerPublicId,
            @AuthenticationPrincipal UserPrincipal userDetails
    ) {
        RelationResponse response = relationService.findRelationByManagerAndCustomer(managerPublicId, customerPublicId, userDetails);
        return ApiResponse.ok(response);
    }

    @GetMapping("/manager")
    public ResponseEntity<ApiResponse<List<RelationResponse>>> findAllRelationByManager(
            @AuthenticationPrincipal UserPrincipal userDetails
    ) {
        List<RelationResponse> responses = relationService.findAllRelationsByManager(userDetails);
        return ApiResponse.ok(responses);
    }

    @GetMapping("/customer/{customerPublicId}")
    public ResponseEntity<ApiResponse<List<RelationResponse>>> findAllRelationByCustomer(
            @PathVariable UUID customerPublicId,
            @AuthenticationPrincipal UserPrincipal userDetails
    ) {
        List<RelationResponse> responses = relationService.findAllRelationsByCustomer(customerPublicId, userDetails);
        return ApiResponse.ok(responses);
    }

    @PatchMapping("/{relationPublicId}/status")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<RelationResponse>> updateRelationStatus(
            @PathVariable UUID relationPublicId,
            @Valid @RequestBody RelationStatusUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal userDetails
    ) {
        RelationResponse response = relationService.updateRelationsStatus(relationPublicId, request.status(), userDetails);
        return ApiResponse.ok(response);
    }

    @DeleteMapping("/{relationPublicId}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> deleteRelation(
            @PathVariable UUID relationPublicId,
            @AuthenticationPrincipal UserPrincipal userDetails
    ) {
        relationService.deleteRelation(relationPublicId, userDetails);
        return ApiResponse.noContent();
    }
}
