package carelog.carelog.identity.app;

import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import carelog.carelog.identity.app.port.UpdatedPasswordCredential;
import carelog.carelog.identity.domain.PasswordCredential;
import carelog.carelog.identity.domain.PasswordCredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordCredentialUpdateServiceTest {

    @Mock private PasswordCredentialRepository passwordCredentialRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private PasswordCredentialUpdateService service;

    @BeforeEach
    void setUp() {
        service = new PasswordCredentialUpdateService(passwordCredentialRepository, passwordEncoder);
    }

    @DisplayName("accountId에 해당하는 Credential이 있으면 새 비밀번호를 1회 Encoding해 갱신하고 그 해시를 반환한다")
    @Test
    void updatePassword_success_encodesOnceAndReturnsHash() {
        UUID accountId = UUID.randomUUID();
        PasswordCredential credential = PasswordCredential.create(accountId, "manager1", "old-hash");
        when(passwordCredentialRepository.findById(accountId)).thenReturn(Optional.of(credential));
        when(passwordEncoder.encode("newRawPassword")).thenReturn("new-hash");

        UpdatedPasswordCredential result = service.updatePassword(accountId, "newRawPassword");

        assertThat(result.encodedPassword()).isEqualTo("new-hash");
        verify(passwordEncoder, times(1)).encode("newRawPassword");

        ArgumentCaptor<PasswordCredential> captor = ArgumentCaptor.forClass(PasswordCredential.class);
        verify(passwordCredentialRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("new-hash");
        assertThat(credential.getPasswordHash()).isEqualTo("new-hash");
    }

    @DisplayName("accountId에 해당하는 Credential이 없으면 USER_NOT_FOUND를 던지고 아무것도 저장하지 않는다")
    @Test
    void updatePassword_missingCredential_throwsWithoutSideEffects() {
        UUID accountId = UUID.randomUUID();
        when(passwordCredentialRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updatePassword(accountId, "newRawPassword"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.USER_NOT_FOUND);

        verifyNoInteractions(passwordEncoder);
        verify(passwordCredentialRepository, never()).save(any());
    }
}
