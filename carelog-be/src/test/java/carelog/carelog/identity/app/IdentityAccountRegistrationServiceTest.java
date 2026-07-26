package carelog.carelog.identity.app;

import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import carelog.carelog.identity.app.port.IdentityAccount;
import carelog.carelog.identity.domain.PasswordCredential;
import carelog.carelog.identity.domain.PasswordCredentialRepository;
import carelog.carelog.identity.domain.PlatformAccount;
import carelog.carelog.identity.domain.PlatformAccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdentityAccountRegistrationServiceTest {

    @Mock private PlatformAccountRepository platformAccountRepository;
    @Mock private PasswordCredentialRepository passwordCredentialRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private IdentityAccountRegistrationService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new IdentityAccountRegistrationService(
                platformAccountRepository, passwordCredentialRepository, passwordEncoder);
    }

    @DisplayName("신규 loginId면 PlatformAccount·PasswordCredential을 생성하고 accountId를 반환한다")
    @Test
    void registerPasswordAccount_success_createsAccountAndCredential() {
        when(passwordCredentialRepository.existsByLoginId("manager1")).thenReturn(false);
        when(passwordEncoder.encode("raw-password")).thenReturn("encoded-hash");

        IdentityAccount result = service.registerPasswordAccount("manager1", "m1@example.com", "raw-password");

        assertThat(result.loginId()).isEqualTo("manager1");
        assertThat(result.encodedPasswordHash()).isEqualTo("encoded-hash");
        assertThat(result.accountId()).isNotNull();

        ArgumentCaptor<PlatformAccount> accountCaptor = ArgumentCaptor.forClass(PlatformAccount.class);
        verify(platformAccountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getPrimaryEmail()).isEqualTo("m1@example.com");
        assertThat(accountCaptor.getValue().getId()).isEqualTo(result.accountId());

        ArgumentCaptor<PasswordCredential> credentialCaptor = ArgumentCaptor.forClass(PasswordCredential.class);
        verify(passwordCredentialRepository).save(credentialCaptor.capture());
        assertThat(credentialCaptor.getValue().getAccountId()).isEqualTo(result.accountId());
        assertThat(credentialCaptor.getValue().getLoginId()).isEqualTo("manager1");
        assertThat(credentialCaptor.getValue().getPasswordHash()).isEqualTo("encoded-hash");
    }

    @DisplayName("이미 존재하는 loginId면 DUPLICATE_USER_ID를 던지고 아무것도 저장하지 않는다")
    @Test
    void registerPasswordAccount_duplicateLoginId_throwsWithoutSideEffects() {
        when(passwordCredentialRepository.existsByLoginId("manager1")).thenReturn(true);

        assertThatThrownBy(() -> service.registerPasswordAccount("manager1", "m1@example.com", "raw-password"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.DUPLICATE_USER_ID);

        verifyNoInteractions(platformAccountRepository);
        verify(passwordCredentialRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }
}
