package carelog.carelog.identity.app.port;

/**
 * Password Credential 갱신 결과. Port 계약이므로 Entity를 노출하지 않는다.
 *
 * <p>{@code encodedPassword}는 Carelog Enrollment Projection이 기존 {@code users.password}
 * 컬럼(호환 기간 동안 유지, 인증에는 더 이상 사용하지 않음)을 같은 해시로 채우기 위해 재사용한다.
 * 평문 비밀번호는 이 계약 어디에도 담기지 않는다.
 */
public record UpdatedPasswordCredential(String encodedPassword) {
}
