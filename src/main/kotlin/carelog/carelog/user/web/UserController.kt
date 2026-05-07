package carelog.carelog.user.web

import carelog.carelog.auth.app.CustomUserDetails
import carelog.carelog.common.web.dto.response.ApiResponse
import carelog.carelog.user.app.UserService
import carelog.carelog.user.web.dto.CustomerCreateRequest
import carelog.carelog.user.web.dto.ManagerCreateRequest
import carelog.carelog.user.web.dto.UserResponse
import carelog.carelog.user.web.dto.UserUpdateRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/users")
class UserController(
    private val userService: UserService,
) {
    @PostMapping("/managers")
    fun createManager(@Valid @RequestBody request: ManagerCreateRequest): ResponseEntity<ApiResponse<UserResponse>> =
        ApiResponse.created(userService.createManager(request))

    @PostMapping("/customers")
    fun createCustomer(
        @Valid @RequestBody request: CustomerCreateRequest,
        @AuthenticationPrincipal userDetails: CustomUserDetails,
    ): ResponseEntity<ApiResponse<UserResponse>> =
        ApiResponse.created(userService.createCustomer(request, userDetails))

    @GetMapping("/customers")
    fun findAllCustomers(
        @RequestParam(required = false) name: String?,
    ): ResponseEntity<ApiResponse<List<UserResponse>>> =
        ApiResponse.ok(userService.findAllCustomers(name))

    @GetMapping("/user-id/{userId}")
    fun findUserByUserId(@PathVariable userId: String): ResponseEntity<ApiResponse<UserResponse>> =
        ApiResponse.ok(userService.findUserByUserId(userId))

    @GetMapping("/email/{email}")
    fun findUserByEmail(@PathVariable email: String): ResponseEntity<ApiResponse<UserResponse>> =
        ApiResponse.ok(userService.findUserByEmail(email))

    @PatchMapping("/{publicId}")
    fun updateUser(
        @PathVariable publicId: UUID,
        @Valid @RequestBody request: UserUpdateRequest,
    ): ResponseEntity<ApiResponse<UserResponse>> =
        ApiResponse.ok(userService.updateUser(publicId, request))

    @DeleteMapping("/{publicId}")
    fun deleteUser(@PathVariable publicId: UUID): ResponseEntity<Void> {
        userService.deleteUser(publicId)
        return ApiResponse.noContent()
    }
}
