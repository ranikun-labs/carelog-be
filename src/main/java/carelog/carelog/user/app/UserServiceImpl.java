package carelog.carelog.user.app;

import carelog.carelog.auth.app.CustomUserDetails;
import carelog.carelog.common.web.exception.*;
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

    private User findUserEntityByPublicId(UUID publicId) {
        return userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new CustomException(ExceptionStatus.USER_NOT_FOUND));
    }

    @Override
    @Transactional
    public UserResponse createManager(ManagerCreateRequest request) {
        if (userRepository.existsByUserId(request.userId())) {
            throw new CustomException(ExceptionStatus.DUPLICATE_USER_ID);
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new CustomException(ExceptionStatus.DUPLICATE_EMAIL);
        }

        User newUser = User.builder()
                .userId(request.userId())
                .password(passwordEncoder.encode(request.password()))
                .email(request.email())
                .name(request.name())
                .role(UserRole.MANAGER)
                .managerType(request.managerType())
                .phoneEncrypted(request.phoneEncrypted())
                .addressEncrypted(request.addressEncrypted())
                .build();

        newUser.assignOrganization(UUID.randomUUID());
        User savedUser = userRepository.save(newUser);
        return UserResponse.from(savedUser);
    }

    @Override
    @Transactional
    public UserResponse createCustomer(CustomerCreateRequest request, CustomUserDetails userDetails) {
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
            user.updatePassword(passwordEncoder.encode(request.password()));
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