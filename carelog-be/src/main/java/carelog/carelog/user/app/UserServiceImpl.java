package carelog.carelog.user.app;

import carelog.carelog.auth.app.UserPrincipal;
import carelog.carelog.common.web.exception.*;
import carelog.carelog.identity.app.port.IdentityAccount;
import carelog.carelog.identity.app.port.IdentityAccountRegistrationPort;
import carelog.carelog.identity.app.port.PasswordCredentialUpdatePort;
import carelog.carelog.identity.app.port.UpdatedPasswordCredential;
import carelog.carelog.user.domain.*;
import carelog.carelog.user.web.dto.*;
import lombok.*;
import org.springframework.security.crypto.password.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final IdentityAccountRegistrationPort identityAccountRegistrationPort;
    private final PasswordCredentialUpdatePort passwordCredentialUpdatePort;

    private User findUserEntityByPublicId(UUID publicId) {
        return userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new CustomException(ExceptionStatus.USER_NOT_FOUND));
    }

    @Override
    @Transactional
    public UserResponse createManager(ManagerCreateRequest request) {
        // Carelog Enrollment Coordinator: CRM(email) 중복 확인 → Identity Account Registration →
        // Carelog Enrollment Projection(User 행 생성 + accountId 연결). 단일 로컬 Transaction.
        if (userRepository.existsByUserId(request.userId())) {
            throw new CustomException(ExceptionStatus.DUPLICATE_USER_ID);
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new CustomException(ExceptionStatus.DUPLICATE_EMAIL);
        }

        IdentityAccount account = identityAccountRegistrationPort.registerPasswordAccount(
                request.userId(), request.email(), request.password());

        User newUser = User.builder()
                .userId(request.userId())
                // Legacy 호환 미러: 인증에는 더 이상 사용하지 않는다(password_credentials가 유일 소스).
                // Identity Registration이 이미 계산한 해시를 재사용해 이중 인코딩을 피한다.
                .password(account.encodedPasswordHash())
                .email(request.email())
                .name(request.name())
                .role(UserRole.MANAGER)
                .managerType(request.managerType())
                .phoneEncrypted(request.phoneEncrypted())
                .addressEncrypted(request.addressEncrypted())
                .build();

        newUser.assignOrganization(UUID.randomUUID());
        // Backfill(V3)이 기존 MANAGER에서 보존한 accountId == publicId 불변식을 신규 가입에도 유지한다.
        newUser.assignPublicId(account.accountId());
        newUser.assignAccountId(account.accountId());
        User savedUser = userRepository.save(newUser);
        return UserResponse.from(savedUser);
    }

    @Override
    @Transactional
    public UserResponse createCustomer(CustomerCreateRequest request, UserPrincipal userDetails) {
        User newUser = User.builder()
                .name(request.name())
                .role(UserRole.CUSTOMER)
                .build();

        newUser.assignOrganization(userDetails.getOrganizationId());
        User savedUser = userRepository.save(newUser);
        return UserResponse.from(savedUser);
    }

    @Override
    public UserResponse findUserByUserId(String userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ExceptionStatus.USER_NOT_FOUND));
        return UserResponse.from(user);
    }

    @Override
    public UserResponse findUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ExceptionStatus.USER_NOT_FOUND));
        return UserResponse.from(user);
    }

    @Transactional
    @Override
    public UserResponse updateUser(UUID publicId, UserUpdateRequest request) {
        User user = findUserEntityByPublicId(publicId);

        if (request.password() != null && !request.password().isBlank()) {
            if (user.getAccountId() != null) {
                // Identity Principal(MANAGER): Credential 정본을 먼저 갱신하고, 같은 해시를
                // Legacy Mirror(users.password)에 재사용한다(이중 인코딩 없음). 실패 시 이 Transaction
                // 전체가 Rollback되어 users.password만 바뀌고 Credential은 그대로인 상태가 나오지 않는다.
                UpdatedPasswordCredential updated =
                        passwordCredentialUpdatePort.updatePassword(user.getAccountId(), request.password());
                user.updatePassword(updated.encodedPassword());
            } else {
                // accountId가 없는 행(CUSTOMER 등)은 Identity Principal이 아니다 — Credential을
                // 새로 만들지 않고 기존 계약대로 Legacy 컬럼만 갱신한다.
                user.updatePassword(passwordEncoder.encode(request.password()));
            }
        }
        if (request.phoneEncrypted() != null) {
            user.updatePhoneEncrypted(request.phoneEncrypted());
        }
        if (request.addressEncrypted() != null) {
            user.updateAddressEncrypted(request.addressEncrypted());
        }

        return UserResponse.from(user);
    }

    @Override
    @Transactional
    public void deleteUser(UUID publicId) {
        User user = findUserEntityByPublicId(publicId);
        userRepository.delete(user);
    }

    @Override
    public List<UserResponse> findAllCustomers(String name) {
        List<User> customers = (name != null && !name.isBlank()) ?
                userRepository.findAllByRoleAndNameContaining(UserRole.CUSTOMER, name)
                : userRepository.findAllByRole(UserRole.CUSTOMER);

        return customers.stream()
                .map(UserResponse::from)
                .toList();
    }
}