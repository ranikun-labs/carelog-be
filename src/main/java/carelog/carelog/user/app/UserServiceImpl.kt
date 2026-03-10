package carelog.carelog.user.app

import carelog.carelog.common.web.exception.CustomException
import carelog.carelog.common.web.exception.ExceptionStatus
import carelog.carelog.user.domain.User
import carelog.carelog.user.domain.UserRepository
import carelog.carelog.user.domain.UserRole
import carelog.carelog.user.web.dto.UserCreateRequest
import carelog.carelog.user.web.dto.UserResponse
import carelog.carelog.user.web.dto.UserUpdateRequest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional


@Service
@Transactional(readOnly = true)
class UserServiceImpl(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
): UserService {


    private fun findUserEntityById(id: Long): User =
         userRepository.findById(id)
                .orElseThrow { CustomException(ExceptionStatus.USER_NOT_FOUND)}

    @Transactional
    override fun createUser(request: UserCreateRequest): UserResponse {
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
            phoneEncrypted = request.phoneEncrypted,
            addressEncrypted = request.addressEncrypted
        )

        val savedUser: User = userRepository.save(newUser)
        return UserResponse.from(savedUser)
    }

    override fun findUserById(id: Long): UserResponse =
          UserResponse.from(findUserEntityById(id))


    override fun findUserByUserId(userId: String): UserResponse =
        userRepository.findByUserId(userId)
            .map{ UserResponse.from(it) }
            .orElseThrow { CustomException(ExceptionStatus.USER_NOT_FOUND)}

    override fun findUserByEmail(email: String): UserResponse =
        userRepository.findByEmail(email)
            .map { UserResponse.from(it) }
            .orElseThrow{ CustomException(ExceptionStatus.USER_NOT_FOUND) }

    @Transactional
    override fun  updateUser(id: Long, request: UserUpdateRequest): UserResponse {
        val user = findUserEntityById(id)

        request.password?.takeIf { it.isNotBlank() }?.let { user.updatePassword(it) }
        request.phoneEncrypted?.let { user.updatePhoneEncrypted(it) }
        request.addressEncrypted?.let { user.updateAddressEncrypted(it) }

        return UserResponse.from(user)
    }

    @Transactional
    override fun deleteUser(id: Long) {
        val user = findUserEntityById(id)
        userRepository.delete(user)
    }
}
