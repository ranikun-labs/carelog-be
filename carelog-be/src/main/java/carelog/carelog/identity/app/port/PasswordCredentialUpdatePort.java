package carelog.carelog.identity.app.port;

import java.util.UUID;

/**
 * 기존 Identity Account의 로그인 비밀번호 변경 경계.
 *
 * <p>accountId(Stable Account ID)로 자격증명을 식별한다 — loginId가 아니라 accountId를 우선한다.
 * CUSTOMER처럼 accountId가 없는 Carelog User는 애초에 이 Port의 대상이 아니다(호출자가 accountId
 * 존재 여부로 먼저 걸러야 한다). 비밀번호는 이 Port 구현 내부에서 단 한 번만 Encoding한다 — 호출자는
 * 반환된 해시를 재사용해야 하며, 별도로 다시 Encoding하지 않는다.
 */
public interface PasswordCredentialUpdatePort {

    /**
     * 주어진 accountId의 Password Credential을 새 비밀번호로 갱신한다.
     *
     * <p>해당 accountId에 등록된 Credential이 없으면 현재 계약과 동일하게 {@code USER_NOT_FOUND}로
     * 매핑되어야 한다(정상적으로는 발생하지 않아야 할 데이터 정합성 이상 상태).
     */
    UpdatedPasswordCredential updatePassword(UUID accountId, String rawPassword);
}
