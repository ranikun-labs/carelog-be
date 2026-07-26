package carelog.carelog.identity;

import carelog.carelog.CarelogApplication;
import carelog.carelog.PostgreSqlTestContainerConfiguration;
import carelog.carelog.identity.app.port.PasswordCredentialUpdatePort;
import carelog.carelog.identity.domain.PasswordCredentialRepository;
import carelog.carelog.user.app.UserService;
import carelog.carelog.user.domain.ManagerType;
import carelog.carelog.user.domain.User;
import carelog.carelog.user.domain.UserRepository;
import carelog.carelog.user.web.dto.ManagerCreateRequest;
import carelog.carelog.user.web.dto.UserUpdateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Password Credential 갱신이 실패할 때 매니저 비밀번호 변경 전체가 단일 Transaction으로 Rollback되어
 * users.password에도 흔적이 남지 않는지 검증한다. {@link ManagerRegistrationRollbackIntegrationTest}와
 * 동일한 방식으로 {@link PasswordCredentialUpdatePort}를 항상 예외를 던지는 Test Double로 교체한다.
 *
 * <p>역방향(User 저장이 실패하고 Credential 갱신은 이미 반영된 경우)은 별도로 재현하지 않는다 — 두
 * 변경 모두 같은 {@code @Transactional} 경계 안, 같은 JPA Persistence Context에서 커밋 전 상태로만
 * 존재하므로, 어느 한쪽에서 발생한 예외든 Spring의 표준 {@code @Transactional} rollback-only 규칙에
 * 따라 전체가 롤백된다 — 이는 Phase A가 새로 만든 보장이 아니라 프레임워크가 이미 제공하는 보장이며,
 * 이 테스트가 그 경계가 실제로 걸려 있음을(credential 실패 방향으로) 실증한다.
 */
@SpringBootTest(classes = CarelogApplication.class)
@ActiveProfiles("test")
@Import({PostgreSqlTestContainerConfiguration.class,
        PasswordChangeRollbackIntegrationTest.FailingCredentialUpdatePortConfig.class})
@Transactional
class PasswordChangeRollbackIntegrationTest {

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordCredentialRepository passwordCredentialRepository;

    @TestConfiguration
    static class FailingCredentialUpdatePortConfig {
        @Bean
        @Primary
        PasswordCredentialUpdatePort failingPasswordCredentialUpdatePort() {
            return (accountId, rawPassword) -> {
                throw new RuntimeException("simulated credential update failure");
            };
        }
    }

    @DisplayName("Credential 갱신이 실패하면 단일 Transaction이 Rollback되어 users.password도 바뀌지 않는다")
    @Test
    void updateUser_credentialFailure_rollsBackWholeTransaction() {
        String userId = "it-pwrollback-" + UUID.randomUUID().toString().substring(0, 8);
        ManagerCreateRequest request = new ManagerCreateRequest(
                userId, userId + "@example.com", "originalPassword1!", "롤백테스트",
                ManagerType.PHYSICAL_THERAPIST, null, null);
        var created = userService.createManager(request);

        User beforeAttempt = userRepository.findByUserId(userId).orElseThrow();
        String passwordBefore = beforeAttempt.getPassword();
        String phoneBefore = beforeAttempt.getPhoneEncrypted();

        UserUpdateRequest updateRequest = new UserUpdateRequest("newPassword2!", "encrypted-phone-should-not-apply", null);

        assertThatThrownBy(() -> userService.updateUser(created.publicId(), updateRequest))
                .isInstanceOf(RuntimeException.class);

        User afterFailedAttempt = userRepository.findByUserId(userId).orElseThrow();
        assertThat(afterFailedAttempt.getPassword()).isEqualTo(passwordBefore);
        assertThat(afterFailedAttempt.getPhoneEncrypted()).isEqualTo(phoneBefore);
        assertThat(passwordCredentialRepository.findById(afterFailedAttempt.getAccountId()))
                .get()
                .extracting(pc -> pc.getPasswordHash())
                .isNotEqualTo("newPassword2!");
    }
}
