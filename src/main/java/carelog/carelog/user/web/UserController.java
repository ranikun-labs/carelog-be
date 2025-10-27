package carelog.carelog.user.web;

import carelog.carelog.common.web.dto.response.ApiResponse;
import carelog.carelog.user.app.UserService;
import carelog.carelog.user.web.dto.UserCreateRequest;
import carelog.carelog.user.web.dto.UserResponse;
import carelog.carelog.user.web.dto.UserUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody UserCreateRequest request) {
        UserResponse response = userService.createUser(request);
        return ApiResponse.created(response);
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

