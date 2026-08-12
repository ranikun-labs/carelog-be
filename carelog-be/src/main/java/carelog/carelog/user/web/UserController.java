package carelog.carelog.user.web;

import carelog.carelog.common.web.dto.response.ApiResponse;
import carelog.carelog.user.app.UserService;
import carelog.carelog.user.web.dto.ManagerCreateRequest;
import carelog.carelog.user.web.dto.UserResponse;
import carelog.carelog.user.web.dto.UserUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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

    @PatchMapping("/{publicId}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable UUID publicId, @Valid @RequestBody UserUpdateRequest request) {
        UserResponse response = userService.updateUser(publicId, request);
        return ApiResponse.ok(response);
    }

    @DeleteMapping("/{publicId}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID publicId) {
        userService.deleteUser(publicId);
        return ApiResponse.noContent();
    }

}
