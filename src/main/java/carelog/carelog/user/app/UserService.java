package carelog.carelog.user.app;

import carelog.carelog.user.web.dto.UserCreateRequest;
import carelog.carelog.user.web.dto.UserResponse;

public interface UserService {
    UserResponse createUser(UserCreateRequest request);
    UserResponse findUserById(Long id);
    UserResponse findUserByUserId(String userId);
    UserResponse findUserByEmail(String email);
}
