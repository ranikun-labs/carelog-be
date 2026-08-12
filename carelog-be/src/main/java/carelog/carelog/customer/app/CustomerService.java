package carelog.carelog.customer.app;

import carelog.carelog.customer.web.dto.CustomerCreateRequest;
import carelog.carelog.customer.web.dto.CustomerResponse;
import carelog.carelog.customer.web.dto.CustomerUpdateRequest;

import java.util.List;
import java.util.UUID;

public interface CustomerService {

    CustomerResponse create(CustomerCreateRequest request, UUID organizationId);

    List<CustomerResponse> findAll(UUID organizationId, String name);

    CustomerResponse findByPublicId(UUID organizationId, UUID publicId);

    CustomerResponse update(UUID organizationId, UUID publicId, CustomerUpdateRequest request);
}
