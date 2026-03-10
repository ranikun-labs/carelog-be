package carelog.carelog.user.web

import carelog.carelog.common.web.dto.response.ApiResponse
import carelog.carelog.user.app.UserService
import carelog.carelog.user.web.dto.UserCreateRequest
import carelog.carelog.user.web.dto.UserResponse
import carelog.carelog.user.web.dto.UserUpdateRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/users")
class UserController (
    private val userService: UserService
){

    @PostMapping
    fun createUser(@Valid @RequestBody request: UserCreateRequest):
    ResponseEntity<ApiResponse<UserResponse>> =
         ApiResponse.created(userService.createUser(request))

    @GetMapping("/user-id/{userId}")
    fun findUserByUserId(@PathVariable userId: String):
            ResponseEntity<ApiResponse<UserResponse>> =
        ApiResponse.ok(userService.findUserByUserId(userId))

    @GetMapping("/email/{email}")
    fun findUserByEmail(@PathVariable email: String):
            ResponseEntity<ApiResponse<UserResponse>> =
        ApiResponse.ok(userService.findUserByEmail(email))

    @PatchMapping("/{id}")
    fun updateUser(
        @PathVariable id: Long,
        @Valid @RequestBody request: UserUpdateRequest
    ): ResponseEntity<ApiResponse<UserResponse>> =
        ApiResponse.ok(userService.updateUser(id, request))

    @DeleteMapping("/{id}")
    fun deleteUser(@PathVariable id: Long): ResponseEntity<Void> {
        userService.deleteUser(id)
        return ApiResponse.noContent()
    }
}

