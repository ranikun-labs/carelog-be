package carelog.carelog.auth.app.adapter;

import carelog.carelog.auth.app.port.CRMIdentityClaims;
import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import carelog.carelog.user.domain.ManagerType;
import carelog.carelog.user.domain.User;
import carelog.carelog.user.domain.UserRepository;
import carelog.carelog.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
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
class LegacyCrmIdentityProjectionAdapterTest {

    private static final UUID ORGANIZATION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ACCOUNT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String USER_ID = "manager@example.com";

    @Mock private UserRepository userRepository;

    private LegacyCrmIdentityProjectionAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new LegacyCrmIdentityProjectionAdapter(userRepository);
    }

    private User newManagerUser(String userId) {
        User user = User.builder()
                .userId(userId)
                .email(userId)
                .password("encoded-password")
                .name("Test Manager")
                .role(UserRole.MANAGER)
                .managerType(ManagerType.PHYSICAL_THERAPIST)
                .build();
        user.assignOrganization(ORGANIZATION_ID);
        return user;
    }

    @DisplayName("User 조회 성공 시 organizationId/role/publicId만 CRMIdentityClaims로 Projection한다")
    @Test
    void getIdentityClaims_found_projectsOnlyThreeFields() {
        User user = newManagerUser(USER_ID);
        when(userRepository.findByAccountId(ACCOUNT_ID)).thenReturn(Optional.of(user));

        CRMIdentityClaims claims = adapter.getIdentityClaims(ACCOUNT_ID);

        assertThat(claims.organizationId()).isEqualTo(ORGANIZATION_ID);
        assertThat(claims.role()).isEqualTo(UserRole.MANAGER.name());
        assertThat(claims.publicId()).isEqualTo(user.getPublicId());
        // CRMIdentityClaims는 record이며 User Entity를 감싸거나 참조하지 않는다.
        assertThat(claims).isNotInstanceOf(User.class);
    }

    @DisplayName("User가 없으면 USER_NOT_FOUND로 매핑한다")
    @Test
    void getIdentityClaims_notFound_throwsUserNotFound() {
        when(userRepository.findByAccountId(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.getIdentityClaims(ACCOUNT_ID))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.USER_NOT_FOUND);
    }
}
