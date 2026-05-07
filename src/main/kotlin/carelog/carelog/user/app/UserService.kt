package carelog.carelog.user.app

import carelog.carelog.auth.app.CustomUserDetails
import carelog.carelog.user.web.dto.CustomerCreateRequest
import carelog.carelog.user.web.dto.ManagerCreateRequest
import carelog.carelog.user.web.dto.UserResponse
import carelog.carelog.user.web.dto.UserUpdateRequest
import java.util.UUID

interface UserService {
    fun createManager(request: ManagerCreateRequest): UserResponse
    fun createCustomer(request: CustomerCreateRequest, userDetails: CustomUserDetails): UserResponse
    fun findUserByUserId(userId: String): UserResponse
    fun findUserByEmail(email: String): UserResponse
    fun updateUser(publicId: UUID, request: UserUpdateRequest): UserResponse
    fun deleteUser(publicId: UUID)
    fun findAllCustomers(name: String?): List<UserResponse>
}
