package carelog.carelog.user.app;

import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import carelog.carelog.user.domain.User;
import carelog.carelog.user.domain.UserRepository;
import carelog.carelog.user.web.dto.UserCreateRequest;
import carelog.carelog.user.web.dto.UserResponse;
import carelog.carelog.user.web.dto.UserUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponse createUser(UserCreateRequest request) {
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
                .role(request.getRole())
                .phoneEncrypted(request.getPhoneEncrypted())
                .addressEncrypted(request.getAddressEncrypted())
                .build();

        User savedUser = userRepository.save(newUser);
        return UserResponse.from(savedUser);
    }

    @Override
    public User findUserEntityById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new CustomException(ExceptionStatus.USER_NOT_FOUND));
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
}
