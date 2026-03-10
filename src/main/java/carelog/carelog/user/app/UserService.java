package carelog.carelog.user.app;

import carelog.carelog.user.web.dto.CustomerCreateRequest;
import carelog.carelog.user.web.dto.ManagerCreateRequest;
import carelog.carelog.user.web.dto.UserResponse;
import carelog.carelog.user.web.dto.UserUpdateRequest;

public interface UserService {
    UserResponse createManager(ManagerCreateRequest request);
    UserResponse createCustomer(CustomerCreateRequest request);
    UserResponse findUserById(Long id);
    UserResponse findUserByUserId(String userId);
    UserResponse findUserByEmail(String email);
    UserResponse updateUser(Long id, UserUpdateRequest request);
    void deleteUser(Long id);

}
