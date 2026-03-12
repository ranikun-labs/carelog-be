package carelog.carelog.relation.web;

import carelog.carelog.common.web.dto.response.*;
import carelog.carelog.relation.app.*;
import carelog.carelog.relation.web.dto.*;
import jakarta.validation.*;
import lombok.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/relations")
@RequiredArgsConstructor
public class RelationController {

    private final RelationService relationService;

    // 관계 생성 (managerId는 JWT에서 추출)
    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<RelationResponse>> createRelation(
            @Valid @RequestBody RelationCreateRequest request
    ) {
        RelationResponse response = relationService.createRelation(request.getCustomerPublicId());
        return ApiResponse.created(response);
    }

    // publicId로 관계 단건 조회
    @GetMapping("/{relationPublicId}")
    public ResponseEntity<ApiResponse<RelationResponse>> findRelationByPublicId(
            @PathVariable UUID relationPublicId
    ) {
        RelationResponse response = relationService.findRelationByPublicId(relationPublicId);
        return ApiResponse.ok(response);
    }

    // 매니저-고객 조합으로 관계 조회
    @GetMapping("/manager/{managerPublicId}/customer/{customerPublicId}")
    public ResponseEntity<ApiResponse<RelationResponse>> findRelationByManagerAndCustomer(
            @PathVariable UUID managerPublicId,
            @PathVariable UUID customerPublicId
    ) {
        RelationResponse response = relationService.findRelationByManagerAndCustomer(managerPublicId, customerPublicId);
        return ApiResponse.ok(response);
    }

    // JWT 매니저의 모든 관계 조회
    @GetMapping("/manager")
    public ResponseEntity<ApiResponse<List<RelationResponse>>> findAllRelationByManager() {
        List<RelationResponse> responses = relationService.findAllRelationsByManager();
        return ApiResponse.ok(responses);
    }

    // 고객의 모든 관계 조회
    @GetMapping("/customer/{customerPublicId}")
    public ResponseEntity<ApiResponse<List<RelationResponse>>> findAllRelationByCustomer(
            @PathVariable UUID customerPublicId
    ) {
        List<RelationResponse> responses = relationService.findAllRelationsByCustomer(customerPublicId);
        return ApiResponse.ok(responses);
    }

    // 관계 상태 업데이트
    @PatchMapping("/{relationPublicId}/status")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<RelationResponse>> updateRelationStatus(
            @PathVariable UUID relationPublicId,
            @Valid @RequestBody RelationStatusUpdateRequest request
    ) {
        RelationResponse response = relationService.updateRelationsStatus(relationPublicId, request.getStatus());
        return ApiResponse.ok(response);
    }

    // 관계 삭제
    @DeleteMapping("/{relationPublicId}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> deleteRelation(
            @PathVariable UUID relationPublicId
    ) {
        relationService.deleteRelation(relationPublicId);
        return ApiResponse.noContent();
    }
}