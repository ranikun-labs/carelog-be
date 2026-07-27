package carelog.carelog.auth.app.port.oauth;

/** OAuth state 저장소 장애를 인증 실패와 구분하는 전용 예외다. */
public class OAuthStateStoreUnavailableException extends RuntimeException {

    public OAuthStateStoreUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
