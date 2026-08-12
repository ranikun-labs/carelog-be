package carelog.carelog.customer.web;

import carelog.carelog.auth.app.UserPrincipal;
import carelog.carelog.common.web.dto.response.ApiResponse;
import carelog.carelog.customer.app.CustomerService;
import carelog.carelog.customer.web.dto.CustomerCreateRequest;
import carelog.carelog.customer.web.dto.CustomerResponse;
import carelog.carelog.customer.web.dto.CustomerUpdateRequest;
import jakarta.validation.Valid;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<CustomerResponse>> createCustomer(
            @Valid @RequestBody CustomerCreateRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        CustomerResponse response = customerService.create(request, userPrincipal.getOrganizationId());
        return ApiResponse.created(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<List<CustomerResponse>>> findAllCustomers(
            @RequestParam(required = false) String name,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        List<CustomerResponse> response = customerService.findAll(userPrincipal.getOrganizationId(), name);
        return ApiResponse.ok(response);
    }

    @GetMapping("/{publicId}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<CustomerResponse>> findCustomer(
            @PathVariable UUID publicId,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        CustomerResponse response = customerService.findByPublicId(
                userPrincipal.getOrganizationId(), publicId);
        return ApiResponse.ok(response);
    }

    @PatchMapping("/{publicId}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<CustomerResponse>> updateCustomer(
            @PathVariable UUID publicId,
            @RequestBody CustomerUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        CustomerResponse response = customerService.update(
                userPrincipal.getOrganizationId(), publicId, request);
        return ApiResponse.ok(response);
    }
}
