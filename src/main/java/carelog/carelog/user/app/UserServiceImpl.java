package carelog.carelog.user.app;

import carelog.carelog.auth.app.CustomUserDetails;
import carelog.carelog.common.web.exception.*;
import carelog.carelog.user.domain.*;
import carelog.carelog.user.web.dto.*;
import lombok.*;
import org.springframework.security.core.context.SecurityContextHolder;
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

    private User findUserEntityById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new CustomException(ExceptionStatus.USER_NOT_FOUND));
    }

    @Override
    @Transactional
    public UserResponse createManager(ManagerCreateRequest request) {
        if (userRepository.existsByUserId(request.getUserId())) {
            throw new CustomException(ExceptionStatus.DUPLICATE_USER_ID);
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new CustomException(ExceptionStatus.DUPLICATE_EMAIL);
        }

        User newUser = User.builder()
                .userId(request.getUserId())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .name(request.getName())
                .role(UserRole.MANAGER)
                .managerType(request.getManagerType())
                .phoneEncrypted(request.getPhoneEncrypted())
                .addressEncrypted(request.getAddressEncrypted())
                .build();

        newUser.assignOrganization(UUID.randomUUID());
        User savedUser = userRepository.save(newUser);
        return UserResponse.from(savedUser);
    }

    @Override
    @Transactional
    public UserResponse createCustomer(CustomerCreateRequest request) {

        CustomUserDetails userDetails = (CustomUserDetails) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();

        User newUser = User.builder()
                .name(request.getName())
                .role(UserRole.CUSTOMER)
                .build();


        newUser.assignOrganization(userDetails.getOrganizationId());
        User savedUser = userRepository.save(newUser);
        return UserResponse.from(savedUser);
    }

    @Override
    public UserResponse findUserById(Long id) {
        User user = findUserEntityById(id);
        return UserResponse.from(user);
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
    public UserResponse updateUser(Long id, UserUpdateRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new CustomException(ExceptionStatus.USER_NOT_FOUND));

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.updatePassword(passwordEncoder.encode(request.getPassword()));
        }
        if (request.getPhoneEncrypted() != null) {
            user.updatePhoneEncrypted(request.getPhoneEncrypted());
        }
        if (request.getAddressEncrypted() != null) {
            user.updateAddressEncrypted(request.getAddressEncrypted());
        }

        return UserResponse.from(user);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new CustomException(ExceptionStatus.USER_NOT_FOUND));
        userRepository.delete(user);
    }

    @Override
    public List<UserResponse> findAllCustomers(String name) {
        List<User> customers = (name != null && !name.isBlank() ) ?
                userRepository.findAllByRoleAndNameContaining(UserRole.CUSTOMER, name)
                : userRepository.findAllByRole(UserRole.CUSTOMER);

        return customers.stream()
                .map(UserResponse::from)
                .toList();

    }
}
