package carelog.carelog.auth.app.port.oauth;

import java.util.Optional;

/** External identity 연결을 조회만 하는 경계다. B1 로그인 흐름에는 write 계약이 없다. */
public interface ExternalIdentityLookupPort {

    Optional<LinkedAccountView> findByProviderSubject(String provider, String providerSubject);
}
