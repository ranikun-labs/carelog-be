package carelog.carelog.identity;

import carelog.carelog.CarelogApplication;
import carelog.carelog.PostgreSqlTestContainerConfiguration;
import carelog.carelog.identity.app.port.IdentityAccount;
import carelog.carelog.identity.app.port.IdentityAccountRegistrationPort;
import carelog.carelog.identity.domain.PlatformAccountRepository;
import carelog.carelog.user.app.UserService;
import carelog.carelog.user.domain.ManagerType;
import carelog.carelog.user.domain.UserRepository;
import carelog.carelog.user.web.dto.ManagerCreateRequest;
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
 * Identity Account 등록이 실패할 때 매니저 가입 전체가 단일 Transaction으로 Rollback되는지 검증한다.
 * 실패를 결정적으로 재현하기 위해 {@link IdentityAccountRegistrationPort}를 항상 예외를 던지는
 * Test Double로 교체한다(다른 통합 테스트와 별도 Spring Context로 격리됨).
 */
@SpringBootTest(classes = CarelogApplication.class)
@ActiveProfiles("test")
@Import({PostgreSqlTestContainerConfiguration.class,
        ManagerRegistrationRollbackIntegrationTest.FailingIdentityPortConfig.class})
@Transactional
class ManagerRegistrationRollbackIntegrationTest {

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PlatformAccountRepository platformAccountRepository;

    @TestConfiguration
    static class FailingIdentityPortConfig {
        @Bean
        @Primary
        IdentityAccountRegistrationPort failingIdentityAccountRegistrationPort() {
            return (loginId, email, rawPassword) -> {
                throw new RuntimeException("simulated identity registration failure");
            };
        }
    }

    @DisplayName("Identity Account 등록이 실패하면 단일 Transaction이 Rollback되어 users에도 흔적이 남지 않는다")
    @Test
    void createManager_identityFailure_rollsBackWholeTransaction() {
        long usersBefore = userRepository.count();
        long accountsBefore = platformAccountRepository.count();

        String userId = "it-rollback-" + UUID.randomUUID().toString().substring(0, 8);
        ManagerCreateRequest request = new ManagerCreateRequest(
                userId, userId + "@example.com", "password123!", "롤백테스트",
                ManagerType.PHYSICAL_THERAPIST, null, null);

        assertThatThrownBy(() -> userService.createManager(request))
                .isInstanceOf(RuntimeException.class);

        assertThat(userRepository.existsByUserId(userId)).isFalse();
        assertThat(userRepository.count()).isEqualTo(usersBefore);
        assertThat(platformAccountRepository.count()).isEqualTo(accountsBefore);
    }
}
