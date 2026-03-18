package carelog.carelog.user.app;

import carelog.carelog.auth.app.CustomUserDetails;
import carelog.carelog.user.web.dto.CustomerCreateRequest;
import carelog.carelog.user.web.dto.ManagerCreateRequest;
import carelog.carelog.user.web.dto.UserResponse;
import carelog.carelog.user.web.dto.UserUpdateRequest;

import java.util.List;
import java.util.UUID;

public interface UserService {
    UserResponse createManager(ManagerCreateRequest request);
    UserResponse createCustomer(CustomerCreateRequest request, CustomUserDetails userDetails);
    UserResponse findUserByUserId(String userId);
    UserResponse findUserByEmail(String email);
    UserResponse updateUser(UUID publicId, UserUpdateRequest request);
    void deleteUser(UUID publicId);

    // ----- Customer 조회 -----
    List<UserResponse> findAllCustomers(String name);
}
