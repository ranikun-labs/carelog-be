package carelog.carelog.customer.app;

import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import carelog.carelog.customer.web.dto.CustomerCreateRequest;
import carelog.carelog.customer.web.dto.CustomerResponse;
import carelog.carelog.customer.web.dto.CustomerUpdateRequest;
import carelog.carelog.user.domain.User;
import carelog.carelog.user.domain.UserRepository;
import carelog.carelog.user.domain.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerServiceImpl implements CustomerService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public CustomerResponse create(CustomerCreateRequest request, UUID organizationId) {
        requireOrganizationId(organizationId);
        validateDisplayName(request.displayName());

        User customer = User.builder()
                .name(request.displayName())
                .customerMemo(request.customerMemo())
                .role(UserRole.CUSTOMER)
                .build();
        customer.assignOrganization(organizationId);

        return CustomerResponse.from(userRepository.save(customer));
    }

    @Override
    public List<CustomerResponse> findAll(UUID organizationId, String name) {
        requireOrganizationId(organizationId);

        List<User> customers = name != null && !name.isBlank()
                ? userRepository.findAllByOrganizationIdAndRoleAndNameContaining(
                        organizationId, UserRole.CUSTOMER, name)
                : userRepository.findAllByOrganizationIdAndRole(organizationId, UserRole.CUSTOMER);

        return customers.stream().map(CustomerResponse::from).toList();
    }

    @Override
    public CustomerResponse findByPublicId(UUID organizationId, UUID publicId) {
        return CustomerResponse.from(findCustomer(organizationId, publicId));
    }

    @Override
    @Transactional
    public CustomerResponse update(UUID organizationId, UUID publicId, CustomerUpdateRequest request) {
        User customer = findCustomer(organizationId, publicId);

        if (request.displayName() != null) {
            validateDisplayName(request.displayName());
            customer.updateName(request.displayName());
        }
        if (request.customerMemo() != null) {
            customer.updateCustomerMemo(request.customerMemo());
        }

        return CustomerResponse.from(customer);
    }

    private User findCustomer(UUID organizationId, UUID publicId) {
        if (organizationId == null || publicId == null) {
            throw new CustomException(ExceptionStatus.CUSTOMER_NOT_FOUND);
        }

        return userRepository.findByOrganizationIdAndRoleAndPublicId(
                        organizationId, UserRole.CUSTOMER, publicId)
                .orElseThrow(() -> new CustomException(ExceptionStatus.CUSTOMER_NOT_FOUND));
    }

    private void requireOrganizationId(UUID organizationId) {
        if (organizationId == null) {
            throw new CustomException(ExceptionStatus.ACCESS_DENIED);
        }
    }

    private void validateDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new CustomException(ExceptionStatus.INVALID_CUSTOMER_DISPLAY_NAME);
        }
    }
}
