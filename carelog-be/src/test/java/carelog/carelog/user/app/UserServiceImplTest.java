package carelog.carelog.user.app;

import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import carelog.carelog.identity.app.port.IdentityAccount;
import carelog.carelog.identity.app.port.IdentityAccountRegistrationPort;
import carelog.carelog.identity.app.port.PasswordCredentialUpdatePort;
import carelog.carelog.identity.app.port.UpdatedPasswordCredential;
import carelog.carelog.user.domain.*;
import carelog.carelog.user.web.dto.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;
import org.springframework.security.crypto.password.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 매니저 회원가입 Characterization Test — Identity Foundation Phase A 도입 전 동작을 고정한다.
 * ManagerCreateRequest/UserResponse 계약, 실패 순서(userId → email 중복), organizationId 신규 생성,
 * publicId 보존(User 생성자가 랜덤 발급), 가입 성공 후 Token 미발급(UserResponse에 Token 필드 없음)은
 * 이 Phase에서 변경하지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private IdentityAccountRegistrationPort identityAccountRegistrationPort;

    @Mock
    private PasswordCredentialUpdatePort passwordCredentialUpdatePort;

    @InjectMocks
    private UserServiceImpl userService;

    private ManagerCreateRequest newRequest() {
        return new ManagerCreateRequest(
                "testuser",
                "test@example.com",
                "password123",
                "Test User",
                ManagerType.PHYSICAL_THERAPIST,
                "010-1234-5678",
                "서울특별시 테스트"
        );
    }

    @DisplayName("정상적 사용자 생성 요청시, Identity Account 등록 후 사용자 정보를 저장하고 UserResponse를 반환한다")
    @Test
    void createManager_success() {
        ManagerCreateRequest request = newRequest();
        UUID accountId = UUID.randomUUID();

        when(userRepository.existsByUserId(request.userId())).thenReturn(false);
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(identityAccountRegistrationPort.registerPasswordAccount(
                request.userId(), request.email(), request.password()))
                .thenReturn(new IdentityAccount(accountId, request.userId(), "encoded-hash"));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.createManager(request);

        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo("testuser");
        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.role()).isEqualTo(UserRole.MANAGER);
        // 가입 성공 응답에 Token 관련 필드가 없다 — 현재 계약(가입 후 Token 미발급)을 UserResponse 구조 자체로 고정.
        assertThat(UserResponse.class.getRecordComponents())
                .extracting("name")
                .doesNotContain("accessToken", "refreshToken");

        InOrder inOrder = inOrder(userRepository, identityAccountRegistrationPort);
        inOrder.verify(userRepository).existsByUserId("testuser");
        inOrder.verify(userRepository).existsByEmail("test@example.com");
        inOrder.verify(identityAccountRegistrationPort)
                .registerPasswordAccount("testuser", "test@example.com", "password123");
        inOrder.verify(userRepository).save(any(User.class));
    }

    @DisplayName("생성된 User Entity는 랜덤 organizationId와 Identity Account가 발급한 accountId를 갖는다")
    @Test
    void createManager_success_assignsOrganizationIdAndAccountId() {
        ManagerCreateRequest request = newRequest();
        UUID accountId = UUID.randomUUID();

        when(userRepository.existsByUserId(any())).thenReturn(false);
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(identityAccountRegistrationPort.registerPasswordAccount(any(), any(), any()))
                .thenReturn(new IdentityAccount(accountId, request.userId(), "encoded-hash"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        userService.createManager(request);

        User saved = captor.getValue();
        assertThat(saved.getOrganizationId()).isNotNull();
        assertThat(saved.getAccountId()).isEqualTo(accountId);
        // Backfill(V3)의 publicId==platformAccount.id 불변식을 신규 가입에도 유지한다.
        assertThat(saved.getPublicId()).isEqualTo(accountId);
        // password 컬럼은 Identity Registration이 계산한 해시를 재사용한다(이중 인코딩 없음, 인증에는 미사용).
        assertThat(saved.getPassword()).isEqualTo("encoded-hash");
    }

    @DisplayName("중복된 userId로 사용자 생성 요청 시, DUPLICATE_USER_ID 예외를 발생시키고 이후 로직을 호출하지 않는다")
    @Test
    void createManager_duplicateUserId_throwsExceptionWithoutSideEffects() {
        ManagerCreateRequest request = newRequest();
        when(userRepository.existsByUserId(request.userId())).thenReturn(true);

        assertThatThrownBy(() -> userService.createManager(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.DUPLICATE_USER_ID);

        verify(userRepository, never()).existsByEmail(any());
        verifyNoInteractions(identityAccountRegistrationPort);
        verify(userRepository, never()).save(any());
    }

    @DisplayName("중복된 email로 사용자 생성 요청 시, DUPLICATE_EMAIL 예외를 발생시키고 Identity 등록/저장을 호출하지 않는다")
    @Test
    void createManager_duplicateEmail_throwsExceptionWithoutSideEffects() {
        ManagerCreateRequest request = newRequest();
        when(userRepository.existsByUserId(request.userId())).thenReturn(false);
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> userService.createManager(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.DUPLICATE_EMAIL);

        verifyNoInteractions(identityAccountRegistrationPort);
        verify(userRepository, never()).save(any());
    }

    @DisplayName("Identity Account 등록이 실패하면(예: loginId 중복) 예외가 그대로 전파되고 User는 저장되지 않는다")
    @Test
    void createManager_identityRegistrationFailure_propagatesWithoutSavingUser() {
        ManagerCreateRequest request = newRequest();
        when(userRepository.existsByUserId(request.userId())).thenReturn(false);
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(identityAccountRegistrationPort.registerPasswordAccount(any(), any(), any()))
                .thenThrow(new CustomException(ExceptionStatus.DUPLICATE_USER_ID));

        assertThatThrownBy(() -> userService.createManager(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.DUPLICATE_USER_ID);

        verify(userRepository, never()).save(any());
    }

    private User newManagerWithAccount(UUID accountId) {
        User user = User.builder()
                .userId("manager1")
                .password("legacy-mirror-hash")
                .email("manager1@example.com")
                .name("Manager One")
                .role(UserRole.MANAGER)
                .managerType(ManagerType.PHYSICAL_THERAPIST)
                .build();
        user.assignAccountId(accountId);
        return user;
    }

    private User newCustomerWithoutAccount() {
        return User.builder()
                .name("Customer One")
                .role(UserRole.CUSTOMER)
                .build();
    }

    @DisplayName("accountId가 있는 User(MANAGER)의 비밀번호 변경은 PasswordCredentialUpdatePort로 Credential을 " +
            "먼저 갱신하고, 같은 해시를 Legacy Mirror(users.password)에 재사용한다(재인코딩 없음)")
    @Test
    void updateUser_managerWithAccount_updatesCredentialThenMirrorsSameHash() {
        UUID accountId = UUID.randomUUID();
        User manager = newManagerWithAccount(accountId);
        when(userRepository.findByPublicId(manager.getPublicId())).thenReturn(java.util.Optional.of(manager));
        when(passwordCredentialUpdatePort.updatePassword(accountId, "newPassword123"))
                .thenReturn(new UpdatedPasswordCredential("new-encoded-hash"));

        UserUpdateRequest request = new UserUpdateRequest("newPassword123", null, null);
        userService.updateUser(manager.getPublicId(), request);

        verify(passwordCredentialUpdatePort).updatePassword(accountId, "newPassword123");
        // Legacy Mirror는 Port가 반환한 해시를 그대로 재사용한다 — passwordEncoder.encode를 직접 호출하지 않는다.
        verify(passwordEncoder, never()).encode(any());
        assertThat(manager.getPassword()).isEqualTo("new-encoded-hash");
    }

    @DisplayName("CUSTOMER의 Legacy User 비밀번호 변경 요청은 Product Customer 경계 밖 mutation으로 차단한다")
    @Test
    void updateUser_customerWithoutAccount_isBlockedBeforeCredentialMutation() {
        User customer = newCustomerWithoutAccount();
        when(userRepository.findByPublicId(customer.getPublicId())).thenReturn(java.util.Optional.of(customer));

        UserUpdateRequest request = new UserUpdateRequest("newPassword123", null, null);
        assertThatThrownBy(() -> userService.updateUser(customer.getPublicId(), request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.ACCESS_DENIED);

        verifyNoInteractions(passwordCredentialUpdatePort);
        verifyNoInteractions(passwordEncoder);
        assertThat(customer.getPassword()).isNull();
        assertThat(customer.getAccountId()).isNull();
    }

    @DisplayName("CUSTOMER의 Legacy User 삭제 요청은 soft-delete 전에 차단한다")
    @Test
    void deleteUser_customer_isBlockedBeforeMutation() {
        User customer = newCustomerWithoutAccount();
        when(userRepository.findByPublicId(customer.getPublicId())).thenReturn(java.util.Optional.of(customer));

        assertThatThrownBy(() -> userService.deleteUser(customer.getPublicId()))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.ACCESS_DENIED);

        verify(userRepository, never()).delete(any(User.class));
    }

    @DisplayName("Identity Credential 갱신이 실패하면 예외가 그대로 전파되고 users.password는 변경되지 않는다")
    @Test
    void updateUser_credentialUpdateFailure_propagatesWithoutMirroringPassword() {
        UUID accountId = UUID.randomUUID();
        User manager = newManagerWithAccount(accountId);
        String originalPassword = manager.getPassword();
        when(userRepository.findByPublicId(manager.getPublicId())).thenReturn(java.util.Optional.of(manager));
        when(passwordCredentialUpdatePort.updatePassword(any(), any()))
                .thenThrow(new CustomException(ExceptionStatus.USER_NOT_FOUND));

        UserUpdateRequest request = new UserUpdateRequest("newPassword123", null, null);

        assertThatThrownBy(() -> userService.updateUser(manager.getPublicId(), request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.USER_NOT_FOUND);

        // Port 호출이 예외를 던졌으므로 users.password 미러 갱신 라인에 도달하지 않는다(같은 @Transactional
        // 메서드 안이므로 실제 DB 반영은 어차피 Rollback되지만, 여기서는 호출 자체가 없었음을 고정한다).
        assertThat(manager.getPassword()).isEqualTo(originalPassword);
    }
}
