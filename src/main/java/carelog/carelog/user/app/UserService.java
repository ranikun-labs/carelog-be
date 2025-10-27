package carelog.carelog.user.app;

import carelog.carelog.user.web.dto.UserCreateRequest;
import carelog.carelog.user.web.dto.UserResponse;
import carelog.carelog.user.web.dto.UserUpdateRequest;
import org.springframework.transaction.annotation.Transactional;

public interface UserService {
    UserResponse createUser(UserCreateRequest request);
    UserResponse findUserById(Long id);
    UserResponse findUserByUserId(String userId);
    UserResponse findUserByEmail(String email);
    UserResponse updateUser(Long id, UserUpdateRequest request);
    void deleteUser(Long id);

}
