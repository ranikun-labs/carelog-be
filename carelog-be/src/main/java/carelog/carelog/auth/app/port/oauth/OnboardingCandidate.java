package carelog.carelog.auth.app.port.oauth;

/** B3 onboarding으로 넘길 최소 외부 신원 후보 정보다. */
public record OnboardingCandidate(
        String provider,
        String providerSubject,
        String email,
        boolean emailVerified,
        String displayNameHint,
        String returnTo
) {
}
