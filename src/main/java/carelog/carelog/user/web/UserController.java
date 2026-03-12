package carelog.carelog.user.web;

import carelog.carelog.common.web.dto.response.ApiResponse;
import carelog.carelog.user.app.UserService;
import carelog.carelog.user.web.dto.CustomerCreateRequest;
import carelog.carelog.user.web.dto.ManagerCreateRequest;
import carelog.carelog.user.web.dto.UserResponse;
import carelog.carelog.user.web.dto.UserUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/managers")
    public ResponseEntity<ApiResponse<UserResponse>> createManager(@Valid @RequestBody ManagerCreateRequest request) {
        UserResponse response = userService.createManager(request);
        return ApiResponse.created(response);
    }

    @PostMapping("/customers")
    public ResponseEntity<ApiResponse<UserResponse>> createCustomer(@Valid @RequestBody CustomerCreateRequest request) {
        UserResponse response = userService.createCustomer(request);
        return ApiResponse.created(response);
    }

    @GetMapping("/customers")
    public ResponseEntity<ApiResponse<List<UserResponse>>> findAllCustomers (
            @RequestParam(required = false) String name
    ) {
        List<UserResponse> responses = userService.findAllCustomers(name);
        return ApiResponse.ok(responses);
    }

    @GetMapping("/user-id/{userId}")
    public ResponseEntity<ApiResponse<UserResponse>> findUserByUserId(@PathVariable String userId) {
        UserResponse userResponse = userService.findUserByUserId(userId);
        return ApiResponse.ok(userResponse);
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<ApiResponse<UserResponse>> findUserByEmail(@PathVariable String email) {
        UserResponse response = userService.findUserByEmail(email);
        return ApiResponse.ok(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
         UserResponse response = userService.updateUser(id, request);
         return ApiResponse.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ApiResponse.noContent();
    }

}

