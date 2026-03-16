package carelog.carelog.user.app;

import carelog.carelog.auth.app.CustomUserDetails;
import carelog.carelog.user.web.dto.CustomerCreateRequest;
import carelog.carelog.user.web.dto.ManagerCreateRequest;
import carelog.carelog.user.web.dto.UserResponse;
import carelog.carelog.user.web.dto.UserUpdateRequest;

import java.util.List;

public interface UserService {
    UserResponse createManager(ManagerCreateRequest request);
    UserResponse createCustomer(CustomerCreateRequest request, CustomUserDetails userDetails);
    UserResponse findUserById(Long id);
    UserResponse findUserByUserId(String userId);
    UserResponse findUserByEmail(String email);
    UserResponse updateUser(Long id, UserUpdateRequest request);
    void deleteUser(Long id);

    // ----- Customer 조회 -----
    List<UserResponse> findAllCustomers(String name);
}
