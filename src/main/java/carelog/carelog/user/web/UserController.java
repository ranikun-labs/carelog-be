package carelog.carelog.user.web;

import carelog.carelog.common.web.dto.response.ApiResponse;
import carelog.carelog.user.app.UserService;
import carelog.carelog.user.web.dto.UserCreateRequest;
import carelog.carelog.user.web.dto.UserResponse;
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
}
