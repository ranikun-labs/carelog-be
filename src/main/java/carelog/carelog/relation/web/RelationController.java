package carelog.carelog.relation.web;

import carelog.carelog.common.web.dto.response.*;
import carelog.carelog.relation.app.*;
import carelog.carelog.relation.web.dto.*;
import jakarta.validation.*;
import lombok.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/relations")
@RequiredArgsConstructor
public class RelationController {

    private final RelationService relationService;

    // 관계 생성
    @PostMapping
    public ResponseEntity<ApiResponse<RelationResponse>> createRelation(
            @Valid @RequestBody RelationCreateRequest request
    ) {
        RelationResponse response = relationService.createRelation(
                request.getManagerId(),
                request.getCustomerId()
        );
        return ApiResponse.created(response);
    }

    // ID로 관계 조회
    @GetMapping("/{relationId}")
    public ResponseEntity<ApiResponse<RelationResponse>> findRelationById(
            @PathVariable Long relationId
    ) {
        RelationResponse response = relationService.findRelationById(relationId);
        return ApiResponse.ok(response);
    }

    // 매니저-고객 조합으로 관계 조회
    @GetMapping("/manager/{managerId}/customer/{customerId}")
    public ResponseEntity<ApiResponse<RelationResponse>> findRelationByManagerAndCustomer(
            @PathVariable Long managerId,
            @PathVariable Long customerId
    ) {
        RelationResponse response = relationService.findRelationByManagerAndCustomer(managerId, customerId);
        return ApiResponse.ok(response);
    }

    // 매니저의 모든 관계 조회
    @GetMapping("manager/{managerId}")
    public ResponseEntity<ApiResponse<List<RelationResponse>>> findAllRelationByManager(
            @PathVariable Long managerId
    ) {
        List<RelationResponse> responses = relationService.findAllRelationsByManager(managerId);
        return ApiResponse.ok(responses);
    }

    // 고객의 모든 관계 조회
    @GetMapping("customer/{customerId}")
    public ResponseEntity<ApiResponse<List<RelationResponse>>> findAllRelationByCustomer(
            @PathVariable Long customerId
    ) {
        List<RelationResponse> responses = relationService.findAllRelationsByCustomer(customerId);
        return ApiResponse.ok(responses);
    }

    // 관계 상태 업데이트
    @PatchMapping("/{relationId}/status")
    public ResponseEntity<ApiResponse<RelationResponse>> updateRelationStatus(
            @PathVariable Long relationId,
            @Valid @RequestBody RelationStatusUpdateRequest request
    ) {
        RelationResponse response = relationService.updateRelationsStatus(
                relationId, request.getStatus()
        );
        return ApiResponse.ok(response);
    }

    // 관계 삭제
    @DeleteMapping("/{relationId}")
    public ResponseEntity<Void> deleteRelation(
            @PathVariable Long relationId
    ) {
        relationService.deleteRelation(relationId);
        return ApiResponse.noContent();
    }
}
