package carelog.carelog.auth.app.adapter;

import carelog.carelog.auth.app.UserPrincipal;
import carelog.carelog.auth.app.port.CRMIdentityClaims;
import carelog.carelog.auth.app.port.CRMIdentityProjectionPort;
import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import carelog.carelog.identity.domain.PasswordCredential;
import carelog.carelog.identity.domain.PasswordCredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityCredentialAdapterTest {

    private static final UUID ORGANIZATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PUBLIC_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock private PasswordCredentialRepository passwordCredentialRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private CRMIdentityProjectionPort crmIdentityProjectionPort;

    private IdentityCredentialAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new IdentityCredentialAdapter(
                passwordCredentialRepository, passwordEncoder, crmIdentityProjectionPort);
    }

    @DisplayName("password_credentials 검증 성공 시 CRMIdentityProjectionPort claim으로 UserPrincipal을 구성한다")
    @Test
    void authenticate_success_buildsPrincipalFromPasswordCredentialAndCrmProjection() {
        UUID accountId = UUID.randomUUID();
        PasswordCredential credential = PasswordCredential.create(
                accountId, "manager@example.com", "encoded-hash");
        when(passwordCredentialRepository.findByLoginId("manager@example.com"))
                .thenReturn(Optional.of(credential));
        when(passwordEncoder.matches("raw-password", "encoded-hash")).thenReturn(true);
        when(crmIdentityProjectionPort.getIdentityClaims(accountId))
                .thenReturn(new CRMIdentityClaims(ORGANIZATION_ID, "MANAGER", PUBLIC_ID));

        UserPrincipal result = adapter.authenticate("manager@example.com", "raw-password");

        assertThat(result.getAccountId()).isEqualTo(accountId);
        assertThat(result.getUserId()).isEqualTo("manager@example.com");
        assertThat(result.getOrganizationId()).isEqualTo(ORGANIZATION_ID);
        assertThat(result.getRole()).isEqualTo("MANAGER");
        assertThat(result.getPublicId()).isEqualTo(PUBLIC_ID);
    }

    @DisplayName("loginId에 해당하는 Credential이 없으면 INVALID_CREDENTIALS를 던진다")
    @Test
    void authenticate_missingCredential_throwsInvalidCredentials() {
        when(passwordCredentialRepository.findByLoginId("unknown@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.authenticate("unknown@example.com", "raw-password"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.INVALID_CREDENTIALS);
    }

    @DisplayName("비밀번호가 일치하지 않으면 INVALID_CREDENTIALS를 던지고 CRM Projection을 조회하지 않는다")
    @Test
    void authenticate_wrongPassword_throwsInvalidCredentialsWithoutCrmLookup() {
        PasswordCredential credential = PasswordCredential.create(
                UUID.randomUUID(), "manager@example.com", "encoded-hash");
        when(passwordCredentialRepository.findByLoginId("manager@example.com"))
                .thenReturn(Optional.of(credential));
        when(passwordEncoder.matches("wrong-password", "encoded-hash")).thenReturn(false);

        assertThatThrownBy(() -> adapter.authenticate("manager@example.com", "wrong-password"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.INVALID_CREDENTIALS);
    }
}
