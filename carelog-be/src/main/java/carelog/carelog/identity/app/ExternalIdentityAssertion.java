package carelog.carelog.identity.app;

/**
 * 향후 Provider Adapter(Google/Kakao 등)가 검증을 마친 뒤 반환할 결과의 Provider-neutral 내부 계약.
 *
 * <p>이 Phase에서는 실제 Provider Adapter, authorize/callback Endpoint, Account Linking을
 * 구현하지 않는다 — 계약(Value Object)만 정의해 향후 확장 지점을 준비한다.
 * Carelog가 기본 Provider를 확정한 것처럼 취급하지 않는다({@code provider}는 임의 문자열).
 */
public record ExternalIdentityAssertion(
        String provider,
        String providerSubject,
        String emailSnapshot
) {
}
