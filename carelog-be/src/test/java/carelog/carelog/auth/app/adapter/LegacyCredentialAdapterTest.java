package carelog.carelog.auth.app.adapter;

import carelog.carelog.auth.app.CustomUserDetails;
import carelog.carelog.auth.app.UserPrincipal;
import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import carelog.carelog.user.domain.ManagerType;
import carelog.carelog.user.domain.User;
import carelog.carelog.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyCredentialAdapterTest {

    private static final UUID ORGANIZATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock private AuthenticationManager authenticationManager;
    @Mock private Authentication authentication;

    private LegacyCredentialAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new LegacyCredentialAdapter(authenticationManager);
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

    @DisplayName("인증 성공 시 AuthenticationManager 위임 결과를 UserPrincipal 계약으로 반환한다")
    @Test
    void authenticate_success_returnsUserPrincipalContract() {
        User user = newManagerUser("manager@example.com");
        CustomUserDetails userDetails = new CustomUserDetails(user);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);

        UserPrincipal result = adapter.authenticate("manager@example.com", "raw-password");

        assertThat(result.getUserId()).isEqualTo("manager@example.com");
        assertThat(result.getOrganizationId()).isEqualTo(ORGANIZATION_ID);
        assertThat(result.getRole()).isEqualTo(UserRole.MANAGER.name());
        assertThat(result.getPublicId()).isEqualTo(user.getPublicId());
        // 반환 타입은 인터페이스이며, 호출부는 User(CRM Entity)를 알지 못한다.
        assertThat(result).isNotInstanceOf(User.class);
    }

    @DisplayName("인증 실패 시 AuthenticationException을 INVALID_CREDENTIALS로 변환한다")
    @Test
    void authenticate_failure_mapsToInvalidCredentials() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad credentials"));

        assertThatThrownBy(() -> adapter.authenticate("manager@example.com", "wrong-password"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.INVALID_CREDENTIALS);
    }
}
