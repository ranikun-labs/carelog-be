package carelog.carelog.identity.app.port;

/**
 * Identity Principal(로그인 가능한 Carelog MANAGER) 신규 등록 경계.
 *
 * <p>책임: loginId 중복 확인(password_credentials 기준), PlatformAccount 생성, Password Encoding,
 * PasswordCredential 생성, stable accountId 확보. email 중복 확인은 CRM({@code users.email})이
 * 소유한 제약이므로 이 Port의 책임이 아니다 — 호출자(Carelog Enrollment Coordinator)가 CRM 경계에서
 * 먼저 검증한다.
 */
public interface IdentityAccountRegistrationPort {

    /**
     * 신규 Password 기반 Identity Account를 등록한다.
     *
     * <p>loginId가 이미 존재하면 현재 계약과 동일하게 {@code DUPLICATE_USER_ID}로 매핑되어야 한다.
     */
    IdentityAccount registerPasswordAccount(String loginId, String email, String rawPassword);
}
