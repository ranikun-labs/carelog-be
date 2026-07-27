package carelog.carelog.auth.app.port.oauth;

import java.time.Duration;
import java.util.Optional;

/** OAuth state의 저장과 원자적 1회 소비 경계다. */
public interface OAuthStateStore {

    void save(String state, OAuthStateRecord record, Duration ttl);

    /**
     * 원자적 GETDEL로 state를 소비한다.
     *
     * <p>empty는 미존재·만료·이미 소비된 인증 실패이고, 저장소 장애는
     * {@link OAuthStateStoreUnavailableException}으로 구분해 전파한다.
     */
    Optional<OAuthStateRecord> consume(String state);
}
