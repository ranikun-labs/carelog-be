package carelog.carelog.customer.app;

import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import carelog.carelog.customer.web.dto.CustomerCreateRequest;
import carelog.carelog.customer.web.dto.CustomerResponse;
import carelog.carelog.customer.web.dto.CustomerUpdateRequest;
import carelog.carelog.user.domain.User;
import carelog.carelog.user.domain.UserRepository;
import carelog.carelog.user.domain.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomerServiceImpl customerService;

    @Test
    @DisplayName("고객 생성은 전달받은 organizationId만 소유자로 저장하고 CustomerResponse만 반환한다")
    void create_usesExplicitOrganizationScope() {
        UUID organizationId = UUID.randomUUID();
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerResponse response = customerService.create(
                new CustomerCreateRequest("고객 A", "초기 메모"), organizationId);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getOrganizationId()).isEqualTo(organizationId);
        assertThat(saved.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(saved.getName()).isEqualTo("고객 A");
        assertThat(saved.getCustomerMemo()).isEqualTo("초기 메모");
        assertThat(response).isEqualTo(CustomerResponse.from(saved));
    }

    @Test
    @DisplayName("목록은 role과 organizationId가 포함된 scoped repository method만 사용한다")
    void findAll_usesScopedRepositoryMethod() {
        UUID organizationId = UUID.randomUUID();
        User customer = customer(organizationId, "고객 A");
        when(userRepository.findAllByOrganizationIdAndRole(organizationId, UserRole.CUSTOMER))
                .thenReturn(List.of(customer));

        List<CustomerResponse> result = customerService.findAll(organizationId, null);

        assertThat(result).containsExactly(CustomerResponse.from(customer));
        verify(userRepository).findAllByOrganizationIdAndRole(organizationId, UserRole.CUSTOMER);
    }

    @Test
    @DisplayName("상세 조회는 organizationId·CUSTOMER·publicId가 모두 일치해야 한다")
    void findByPublicId_usesScopedRepositoryMethod() {
        UUID organizationId = UUID.randomUUID();
        User customer = customer(organizationId, "고객 A");
        when(userRepository.findByOrganizationIdAndRoleAndPublicId(
                organizationId, UserRole.CUSTOMER, customer.getPublicId()))
                .thenReturn(java.util.Optional.of(customer));

        assertThat(customerService.findByPublicId(organizationId, customer.getPublicId()))
                .isEqualTo(CustomerResponse.from(customer));
    }

    @Test
    @DisplayName("scoped 상세 조회가 없으면 존재 여부를 노출하지 않고 CUSTOMER_NOT_FOUND를 반환한다")
    void findByPublicId_crossOrganization_isConcealed() {
        UUID organizationId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        when(userRepository.findByOrganizationIdAndRoleAndPublicId(
                organizationId, UserRole.CUSTOMER, publicId))
                .thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> customerService.findByPublicId(organizationId, publicId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.CUSTOMER_NOT_FOUND);
    }

    @Test
    @DisplayName("수정은 displayName·customerMemo 외 입력을 받을 수 없고 scoped entity만 변경한다")
    void update_changesBoundedFields() {
        UUID organizationId = UUID.randomUUID();
        User customer = customer(organizationId, "기존 이름");
        when(userRepository.findByOrganizationIdAndRoleAndPublicId(
                organizationId, UserRole.CUSTOMER, customer.getPublicId()))
                .thenReturn(java.util.Optional.of(customer));

        CustomerResponse response = customerService.update(
                organizationId, customer.getPublicId(), new CustomerUpdateRequest("새 이름", "새 메모"));

        assertThat(customer.getName()).isEqualTo("새 이름");
        assertThat(customer.getCustomerMemo()).isEqualTo("새 메모");
        assertThat(response.displayName()).isEqualTo("새 이름");
        assertThat(response.customerMemo()).isEqualTo("새 메모");
        assertThat(customer.getOrganizationId()).isEqualTo(organizationId);
        assertThat(customer.getRole()).isEqualTo(UserRole.CUSTOMER);
    }

    @Test
    @DisplayName("displayName이 null·blank이면 생성과 수정 모두 거부한다")
    void displayName_blank_isRejected() {
        UUID organizationId = UUID.randomUUID();

        assertThatThrownBy(() -> customerService.create(
                new CustomerCreateRequest("   ", null), organizationId))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.INVALID_CUSTOMER_DISPLAY_NAME);

        User customer = customer(organizationId, "기존 이름");
        when(userRepository.findByOrganizationIdAndRoleAndPublicId(
                organizationId, UserRole.CUSTOMER, customer.getPublicId()))
                .thenReturn(java.util.Optional.of(customer));

        assertThatThrownBy(() -> customerService.update(
                organizationId, customer.getPublicId(), new CustomerUpdateRequest("\t", null)))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.INVALID_CUSTOMER_DISPLAY_NAME);
    }

    private User customer(UUID organizationId, String name) {
        User user = User.builder().name(name).role(UserRole.CUSTOMER).build();
        user.assignOrganization(organizationId);
        return user;
    }
}
