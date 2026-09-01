package carelog.carelog.user.app;

import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import carelog.carelog.user.domain.User;
import carelog.carelog.user.domain.UserRepository;
import carelog.carelog.user.domain.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductIdentityClaimsQueryServiceTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORGANIZATION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PUBLIC_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock
    private UserRepository userRepository;

    @Mock
    private User user;

    @Test
    @DisplayName("accountId로 조회한 Product User를 MANAGER claims snapshot으로 변환한다")
    void existingManager_mapsOnlyClaims() {
        when(userRepository.findByAccountId(ACCOUNT_ID)).thenReturn(Optional.of(user));
        when(user.getOrganizationId()).thenReturn(ORGANIZATION_ID);
        when(user.getRole()).thenReturn(UserRole.MANAGER);
        when(user.getPublicId()).thenReturn(PUBLIC_ID);

        ProductIdentityClaimsQueryService service = new ProductIdentityClaimsQueryService(userRepository);

        ProductIdentityClaimsQueryService.ProductIdentityClaims result = service.getClaims(ACCOUNT_ID);

        assertThat(result.organizationId()).isEqualTo(ORGANIZATION_ID);
        assertThat(result.role()).isEqualTo("MANAGER");
        assertThat(result.role()).isNotEqualTo("ROLE_MANAGER");
        assertThat(result.publicId()).isEqualTo(PUBLIC_ID);
    }

    @Test
    @DisplayName("Product profile이 없으면 USER_NOT_FOUND를 던진다")
    void missingProfile_throwsUserNotFound() {
        when(userRepository.findByAccountId(ACCOUNT_ID)).thenReturn(Optional.empty());

        ProductIdentityClaimsQueryService service = new ProductIdentityClaimsQueryService(userRepository);

        assertThatThrownBy(() -> service.getClaims(ACCOUNT_ID))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.USER_NOT_FOUND);
    }
}
