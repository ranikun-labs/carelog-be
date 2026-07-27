package carelog.carelog.auth.app.port.oauth;

import java.util.UUID;

/** External identity에 연결된 계정의 provider-neutral 조회 결과다. */
public record LinkedAccountView(
        UUID accountId,
        LinkedAccountStatus status
) {
}
