package carelog.carelog.user.app;

import carelog.carelog.user.domain.*;
import carelog.carelog.user.web.dto.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;
import org.springframework.security.crypto.password.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    @DisplayName("정상적 사용자 생성 요청시, 사용자 정보 저장 및 UserResponse 반환")
    @Test
    void createUser_success() {
        // given: 테스트에 필요한 요청 객체와 Mock 객체의 동작을 설정
        ManagerCreateRequest request = ManagerCreateRequest.builder()
                .userId("testuser")
                .password("password123")
                .email("test@example.com")
                .name("Test User")
                .role(UserRole.MANAGER) // 실제 UserRole enum 값으로 대체 필요
                .phoneEncrypted("010-1234-5678")
                .addressEncrypted("서울특별시 테스트")
                .build();

        User newUser = User.builder()
                .userId("testuser")
                .password("encodedPassword")
                .email("test@example.com")
                .role(UserRole.MANAGER)
                .phoneEncrypted("010-1234-5678")
                .addressEncrypted("서울특별시 테스트")
                .build();

        // Mock 객체들의 동작 정의
        when(userRepository.existsByUserId(request.getUserId())).thenReturn(false);
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(newUser);

        // when: 테스트 대상 메소드 호출
        UserResponse response = userService.createUser(request);

        // then : 결과가 예상과 일치하는지 검증
        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo("testuser");
        assertThat(response.getEmail()).isEqualTo("test@example.com");

        // Mock 객체의 메소드가 예상대로 호출됬는지 검증
        verify(userRepository).existsByUserId("testuser");
        verify(userRepository).existsByEmail("test@example.com");
        verify(passwordEncoder).encode("password123");
        verify(userRepository).save(any(User.class));
    }


//    @DisplayName("중복된 userId로 사용자 생성 요청 시, DUPLICATE_USER_ID 예외를 발생시킨다.")
//    @Test
//    void createUser_duplicateUserId_throwsException() {
//        // given: 중복된 userId를 가진 요청을 설정합니다.
//        UserCreateRequest request = UserCreateRequest.builder()
//                .userId("existinguser")
//                .password("password123")
//                .email("test@example.com")
//                .name("Test User")
//                .role(UserRole.MANAGER) // (실제 Enum 값으로)
//                .build();
//
//        // **핵심**: userRepository.existsByUserId()가 true를 반환하도록 '가짜' 행동을 설정합니다.
//        when(userRepository.existsByUserId(request.getUserId())).thenReturn(true);
//
//        // when & then: createUser를 호출했을 때, 예외가 발생하는지 검증합니다.
//        assertThatThrownBy(() -> userService.createUser(request))
//                .isInstanceOf(CustomException.class) // (사용자님이 정의한 CustomException 타입으로)
//                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.DUPLICATE_USER_ID); // (정의한 Enum 상태인지)
//
//        // then: 예외가 발생했으므로, 그 뒤의 로직(email 검사, 암호화, save)은 절대 호출되면 안 됩니다.
//        verify(userRepository).existsByUserId("existinguser"); // userId 중복 검사만 호출
//        verify(userRepository, never()).existsByEmail(any()); // email 검사는 호출되지 않아야 함
//        verify(passwordEncoder, never()).encode(any()); // 비밀번호 암호화는 호출되지 않아야 함
//        verify(userRepository, never()).save(any(User.class)); // User 저장은 호출되지 않아야 함
//    }
//
//
//    @DisplayName("중복된 email로 사용자 생성 요청 시, DUPLICATE_EMAIL 예외를 발생시킨다.")
//    @Test
//    void createUser_duplicateEmail_throwsException() {
//        // given: 중복된 email을 가진 요청을 설정합니다.
//        UserCreateRequest request = UserCreateRequest.builder()
//                .userId("testuser")
//                .password("password123")
//                .email("existing@example.com")
//                .name("Test User")
//                .role(UserRole.MANAGER) // (실제 Enum 값으로)
//                .build();
//
//        // **핵심**: userId는 중복이 없고(false), email은 중복이 있도록(true) 설정합니다.
//        when(userRepository.existsByUserId(request.getUserId())).thenReturn(false);
//        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);
//
//        // when & then: 예외가 발생하는지 검증합니다.
//        assertThatThrownBy(() -> userService.createUser(request))
//                .isInstanceOf(CustomException.class)
//                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.DUPLICATE_EMAIL);
//
//        // then: email 검사까지는 호출되고, 그 뒤의 로직(암호화, save)은 호출되면 안 됩니다.
//        verify(userRepository).existsByUserId("testuser");
//        verify(userRepository).existsByEmail("existing@example.com");
//        verify(passwordEncoder, never()).encode(any());
//        verify(userRepository, never()).save(any(User.class));
//    }

}