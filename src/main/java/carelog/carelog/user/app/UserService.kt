package carelog.carelog.user.app

import carelog.carelog.user.web.dto.UserCreateRequest
import carelog.carelog.user.web.dto.UserResponse
import carelog.carelog.user.web.dto.UserUpdateRequest

interface UserService {
    fun createUser(request: UserCreateRequest): UserResponse
    fun findUserById(id: Long): UserResponse
    fun findUserByUserId(userId: String): UserResponse
    fun findUserByEmail(email: String): UserResponse
    fun updateUser(id: Long, request: UserUpdateRequest): UserResponse
    fun deleteUser(id: Long)
}
