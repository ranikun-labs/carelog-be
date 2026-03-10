package carelog.carelog.user.web.dto

import carelog.carelog.user.domain.User
import carelog.carelog.user.domain.UserRole

data class UserResponse (
    val id: Long,
    val userId: String,
    val email: String,
    val name: String?,
    val role: UserRole,
    val phoneEncrypted: String?,
    val addressEncrypted: String?
){
    companion object{
        fun from(user: User): UserResponse = UserResponse(
            id = user.id,
            userId = user.userId,
            email = user.email,
            name = user.name,
            role = user.role,
            phoneEncrypted = user.phoneEncrypted,
            addressEncrypted = user.addressEncrypted
        )
    }
}
