package carelog.carelog.identity;

import carelog.carelog.CarelogApplication;
import carelog.carelog.PostgreSqlTestContainerConfiguration;
import carelog.carelog.auth.app.AuthService;
import carelog.carelog.auth.app.JwtTokenProvider;
import carelog.carelog.auth.web.dto.request.LoginRequest;
import carelog.carelog.auth.web.dto.response.LoginResponse;
import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.identity.domain.PasswordCredential;
import carelog.carelog.identity.domain.PasswordCredentialRepository;
import carelog.carelog.identity.domain.PlatformAccountRepository;
import carelog.carelog.user.domain.ManagerType;
import carelog.carelog.user.domain.User;
import carelog.carelog.user.domain.UserRepository;
import carelog.carelog.user.domain.UserRole;
import carelog.carelog.user.web.dto.CustomerCreateRequest;
import carelog.carelog.user.web.dto.ManagerCreateRequest;
import carelog.carelog.user.web.dto.UserResponse;
import carelog.carelog.user.web.dto.UserUpdateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Identity Foundation Phase A 통합 회귀: 실제 Spring Context + Testcontainers Postgres로
 * 신규 매니저 가입이 Identity Account/Credential을 만들고, 그 계정으로 실제 로그인이 성공하며
 * 기존 Token Claim 계약이 동일하고, Customer 생성 흐름이 불변인지 검증한다.
 * Identity 등록 실패 시 단일 Transaction Rollback은 {@link ManagerRegistrationRollbackIntegrationTest}가 검증한다.
 */
@SpringBootTest(classes = CarelogApplication.class)
@ActiveProfiles("test")
@Import(PostgreSqlTestContainerConfiguration.class)
class ManagerRegistrationAndLoginIntegrationTest {

    @Autowired
    private carelog.carelog.user.app.UserService userService;
    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PlatformAccountRepository platformAccountRepository;
    @Autowired
    private PasswordCredentialRepository passwordCredentialRepository;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Nested
    @Transactional
    class RegistrationAndLogin {

        @DisplayName("신규 매니저 가입 후 같은 자격증명으로 로그인하면 Identity Account가 발급한 값과 " +
                "CRM(User) claim이 그대로 담긴 Token이 발급된다")
        @Test
        void registerThenLogin_succeedsWithConsistentClaims() {
            String userId = "it-manager-" + UUID.randomUUID().toString().substring(0, 8);
            ManagerCreateRequest request = new ManagerCreateRequest(
                    userId, userId + "@example.com", "password123!", "통합테스트 매니저",
                    ManagerType.PHYSICAL_THERAPIST, null, null);

            UserResponse created = userService.createManager(request);

            User savedUser = userRepository.findByUserId(userId).orElseThrow();
            assertThat(savedUser.getAccountId()).isNotNull();
            // 기존 MANAGER publicId == platformAccount.id (Identity Account = 기존 publicId 보존)
            assertThat(savedUser.getAccountId()).isEqualTo(savedUser.getPublicId());
            assertThat(platformAccountRepository.findById(savedUser.getAccountId())).isPresent();
            assertThat(passwordCredentialRepository.findByLoginId(userId)).isPresent();

            LoginResponse loginResponse = authService.login(new LoginRequest(userId, "password123!"));

            assertThat(loginResponse.accessToken()).isNotBlank();
            assertThat(jwtTokenProvider.getUserIdFromToken(loginResponse.accessToken())).isEqualTo(userId);
            assertThat(jwtTokenProvider.getOrganizationIdFromToken(loginResponse.accessToken()))
                    .isEqualTo(savedUser.getOrganizationId());
            assertThat(jwtTokenProvider.getRoleFromToken(loginResponse.accessToken()))
                    .isEqualTo(UserRole.MANAGER.name());
            assertThat(jwtTokenProvider.getPublicIdFromToken(loginResponse.accessToken()))
                    .isEqualTo(created.publicId());
        }

        @DisplayName("잘못된 비밀번호로는 로그인이 실패한다(INVALID_CREDENTIALS)")
        @Test
        void registerThenLoginWrongPassword_fails() {
            String userId = "it-manager-" + UUID.randomUUID().toString().substring(0, 8);
            ManagerCreateRequest request = new ManagerCreateRequest(
                    userId, userId + "@example.com", "password123!", "통합테스트 매니저",
                    ManagerType.PHYSICAL_THERAPIST, null, null);
            userService.createManager(request);

            assertThatThrownBy(() -> authService.login(new LoginRequest(userId, "wrong-password")))
                    .isInstanceOf(CustomException.class);
        }

        @DisplayName("Customer 생성 흐름은 Identity Account 없이 그대로 동작한다")
        @Test
        void createCustomer_unaffectedByIdentityFoundation() {
            String managerId = "it-manager-" + UUID.randomUUID().toString().substring(0, 8);
            ManagerCreateRequest managerRequest = new ManagerCreateRequest(
                    managerId, managerId + "@example.com", "password123!", "통합테스트 매니저",
                    ManagerType.PHYSICAL_THERAPIST, null, null);
            userService.createManager(managerRequest);
            User manager = userRepository.findByUserId(managerId).orElseThrow();

            carelog.carelog.auth.app.UserPrincipal principal = new carelog.carelog.auth.app.UserPrincipal() {
                @Override public String getUserId() { return manager.getUserId(); }
                @Override public UUID getOrganizationId() { return manager.getOrganizationId(); }
                @Override public String getRole() { return manager.getRole().name(); }
                @Override public UUID getPublicId() { return manager.getPublicId(); }
            };

            UserResponse customer = userService.createCustomer(new CustomerCreateRequest("통합테스트 고객"), principal);

            assertThat(customer.role()).isEqualTo(UserRole.CUSTOMER);
            User savedCustomer = userRepository.findByPublicId(customer.publicId()).orElseThrow();
            assertThat(savedCustomer.getAccountId()).isNull();
        }
    }

    @Nested
    @Transactional
    class PasswordChange {

        @DisplayName("매니저가 비밀번호를 바꾸면 새 비밀번호로만 로그인되고, users.password와 " +
                "password_credentials.password_hash가 동일 Hash를 갖는다(평문은 어디에도 없음)")
        @Test
        void changePassword_managerCanLoginWithNewPasswordOnly_andHashesStayInSync() throws InterruptedException {
            String userId = "it-pwchange-" + UUID.randomUUID().toString().substring(0, 8);
            ManagerCreateRequest request = new ManagerCreateRequest(
                    userId, userId + "@example.com", "oldPassword123!", "비밀번호변경테스트",
                    ManagerType.PHYSICAL_THERAPIST, null, null);
            UserResponse created = userService.createManager(request);

            // 기존 비밀번호 로그인 성공 확인(변경 전 baseline)
            assertThat(authService.login(new LoginRequest(userId, "oldPassword123!")).accessToken()).isNotBlank();

            userService.updateUser(created.publicId(), new UserUpdateRequest("newPassword456!", null, null));

            // JWT는 초 단위 iat/exp를 담으므로, 같은 초 안에 재로그인하면 이전 로그인과 완전히 동일한 토큰
            // 문자열이 나와 refresh_token unique 제약과 충돌할 수 있다(테스트 타이밍 artifact) — 초 경계를
            // 넘겨 서로 다른 iat를 보장한다. 프로덕션 재현성과 무관.
            Thread.sleep(1100);

            // 새 비밀번호 로그인 성공
            assertThat(authService.login(new LoginRequest(userId, "newPassword456!")).accessToken()).isNotBlank();
            // 이전 비밀번호는 더 이상 로그인되지 않는다
            assertThatThrownBy(() -> authService.login(new LoginRequest(userId, "oldPassword123!")))
                    .isInstanceOf(CustomException.class);

            User updated = userRepository.findByUserId(userId).orElseThrow();
            PasswordCredential credential = passwordCredentialRepository.findById(updated.getAccountId()).orElseThrow();

            // users.password(Legacy Mirror) == password_credentials.password_hash(정본) — 같은 해시.
            assertThat(updated.getPassword()).isEqualTo(credential.getPasswordHash());
            // 평문 비밀번호가 어느 컬럼에도 그대로 저장되지 않았다(BCrypt 해시 형식).
            assertThat(updated.getPassword()).isNotEqualTo("newPassword456!");
            assertThat(credential.getPasswordHash()).isNotEqualTo("newPassword456!");
            assertThat(credential.getPasswordHash()).startsWith("$2");
        }

        @DisplayName("Customer 비밀번호 변경 요청은 Credential을 생성하지 않고 accountId는 계속 null이다")
        @Test
        void changePassword_customerNeverCreatesCredential() {
            String managerId = "it-pwchange-mgr-" + UUID.randomUUID().toString().substring(0, 8);
            ManagerCreateRequest managerRequest = new ManagerCreateRequest(
                    managerId, managerId + "@example.com", "password123!", "매니저",
                    ManagerType.PHYSICAL_THERAPIST, null, null);
            userService.createManager(managerRequest);
            User manager = userRepository.findByUserId(managerId).orElseThrow();

            carelog.carelog.auth.app.UserPrincipal principal = new carelog.carelog.auth.app.UserPrincipal() {
                @Override public String getUserId() { return manager.getUserId(); }
                @Override public UUID getOrganizationId() { return manager.getOrganizationId(); }
                @Override public String getRole() { return manager.getRole().name(); }
                @Override public UUID getPublicId() { return manager.getPublicId(); }
            };
            UserResponse customer = userService.createCustomer(new CustomerCreateRequest("고객"), principal);

            long credentialCountBefore = passwordCredentialRepository.count();

            userService.updateUser(customer.publicId(), new UserUpdateRequest("someNewPassword1", null, null));

            User updatedCustomer = userRepository.findByPublicId(customer.publicId()).orElseThrow();
            assertThat(updatedCustomer.getAccountId()).isNull();
            assertThat(passwordCredentialRepository.count()).isEqualTo(credentialCountBefore);
        }
    }
}
