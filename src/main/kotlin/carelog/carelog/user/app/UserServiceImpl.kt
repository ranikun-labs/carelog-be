package carelog.carelog.user.app

import carelog.carelog.auth.app.CustomUserDetails
import carelog.carelog.common.web.exception.CustomException
import carelog.carelog.common.web.exception.ExceptionStatus
import carelog.carelog.user.domain.User
import carelog.carelog.user.domain.UserRepository
import carelog.carelog.user.domain.UserRole
import carelog.carelog.user.web.dto.CustomerCreateRequest
import carelog.carelog.user.web.dto.ManagerCreateRequest
import carelog.carelog.user.web.dto.UserResponse
import carelog.carelog.user.web.dto.UserUpdateRequest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class UserServiceImpl(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) : UserService {

    private fun findUserEntityByPublicId(publicId: UUID): User =
        userRepository.findByPublicId(publicId)
            .orElseThrow { CustomException(ExceptionStatus.USER_NOT_FOUND) }

    @Transactional
    override fun createManager(request: ManagerCreateRequest): UserResponse {
        if (userRepository.existsByUserId(request.userId)) {
            throw CustomException(ExceptionStatus.DUPLICATE_USER_ID)
        }
        if (userRepository.existsByEmail(request.email)) {
            throw CustomException(ExceptionStatus.DUPLICATE_EMAIL)
        }

        val newUser = User(
            userId = request.userId,
            password = passwordEncoder.encode(request.password),
            email = request.email,
            name = request.name,
            role = UserRole.MANAGER,
            managerType = request.managerType,
            phoneEncrypted = request.phoneEncrypted,
            addressEncrypted = request.addressEncrypted,
        )
        newUser.assignOrganization(UUID.randomUUID())
        return UserResponse.from(userRepository.save(newUser))
    }

    @Transactional
    override fun createCustomer(request: CustomerCreateRequest, userDetails: CustomUserDetails): UserResponse {
        val newUser = User(
            name = request.name,
            role = UserRole.CUSTOMER,
        )
        newUser.assignOrganization(userDetails.organizationId)
        return UserResponse.from(userRepository.save(newUser))
    }

    override fun findUserByUserId(userId: String): UserResponse =
        userRepository.findByUserId(userId)
            .map(UserResponse::from)
            .orElseThrow { CustomException(ExceptionStatus.USER_NOT_FOUND) }

    override fun findUserByEmail(email: String): UserResponse =
        userRepository.findByEmail(email)
            .map(UserResponse::from)
            .orElseThrow { CustomException(ExceptionStatus.USER_NOT_FOUND) }

    @Transactional
    override fun updateUser(publicId: UUID, request: UserUpdateRequest): UserResponse {
        val user = findUserEntityByPublicId(publicId)
        request.password?.takeIf { it.isNotBlank() }?.let { user.updatePassword(passwordEncoder.encode(it)) }
        request.phoneEncrypted?.let { user.updatePhoneEncrypted(it) }
        request.addressEncrypted?.let { user.updateAddressEncrypted(it) }
        return UserResponse.from(user)
    }

    @Transactional
    override fun deleteUser(publicId: UUID) {
        val user = findUserEntityByPublicId(publicId)
        userRepository.delete(user)
    }

    override fun findAllCustomers(name: String?): List<UserResponse> {
        val users = if (!name.isNullOrBlank())
            userRepository.findAllByRoleAndNameContaining(UserRole.CUSTOMER, name)
        else
            userRepository.findAllByRole(UserRole.CUSTOMER)
        return users.map(UserResponse::from)
    }
}
