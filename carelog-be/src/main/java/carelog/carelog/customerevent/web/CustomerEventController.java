package carelog.carelog.customerevent.web;

import carelog.carelog.auth.app.UserPrincipal;
import carelog.carelog.common.web.dto.response.ApiResponse;
import carelog.carelog.customerevent.app.CustomerEventService;
import carelog.carelog.customerevent.app.CustomerEventServiceImpl;
import carelog.carelog.customerevent.web.dto.CustomerEventCreateRequest;
import carelog.carelog.customerevent.web.dto.CustomerEventOccurRequest;
import carelog.carelog.customerevent.web.dto.CustomerEventResponse;
import carelog.carelog.customerevent.web.dto.CustomerEventUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/customer-events")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MANAGER')")
public class CustomerEventController {

    private final CustomerEventService customerEventService;

    @PostMapping
    public ResponseEntity<ApiResponse<CustomerEventResponse>> create(
            @RequestBody CustomerEventCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.created(customerEventService.create(principal.getOrganizationId(), request));
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<ApiResponse<CustomerEventResponse>> findById(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(customerEventService.findById(principal.getOrganizationId(), eventId));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CustomerEventResponse>>> findAll(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "50") int limit,
            @AuthenticationPrincipal UserPrincipal principal) {
        OffsetDateTime parsedFrom = from == null ? null : CustomerEventServiceImpl.parseRequiredTime(from);
        OffsetDateTime parsedTo = to == null ? null : CustomerEventServiceImpl.parseRequiredTime(to);
        return ApiResponse.ok(customerEventService.findAll(
                principal.getOrganizationId(), customerId, parsedFrom, parsedTo, limit));
    }

    @PatchMapping("/{eventId}")
    public ResponseEntity<ApiResponse<CustomerEventResponse>> update(
            @PathVariable UUID eventId,
            @RequestBody CustomerEventUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(customerEventService.update(
                principal.getOrganizationId(), eventId, request));
    }

    @PostMapping("/{eventId}/occur")
    public ResponseEntity<ApiResponse<CustomerEventResponse>> occur(
            @PathVariable UUID eventId,
            @RequestBody CustomerEventOccurRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        OffsetDateTime occurredAt = CustomerEventServiceImpl.parseRequiredTime(request.occurredAt());
        return ApiResponse.ok(customerEventService.occur(
                principal.getOrganizationId(), eventId, occurredAt));
    }

    @PostMapping("/{eventId}/cancel")
    public ResponseEntity<ApiResponse<CustomerEventResponse>> cancel(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(customerEventService.cancel(principal.getOrganizationId(), eventId));
    }
}
