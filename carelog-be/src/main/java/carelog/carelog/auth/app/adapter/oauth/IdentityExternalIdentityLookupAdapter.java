package carelog.carelog.auth.app.adapter.oauth;

import carelog.carelog.auth.app.port.oauth.ExternalIdentityLookupPort;
import carelog.carelog.auth.app.port.oauth.LinkedAccountStatus;
import carelog.carelog.auth.app.port.oauth.LinkedAccountView;
import carelog.carelog.identity.domain.AccountStatus;
import carelog.carelog.identity.domain.ExternalIdentityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** ExternalIdentity와 PlatformAccount를 한 번에 조회하는 OAuth read-only adapter다. */
@Component
@RequiredArgsConstructor
public class IdentityExternalIdentityLookupAdapter implements ExternalIdentityLookupPort {

    private final ExternalIdentityRepository externalIdentityRepository;

    @Override
    public Optional<LinkedAccountView> findByProviderSubject(String provider, String providerSubject) {
        return externalIdentityRepository.findLinkedAccountByProviderAndProviderSubject(provider, providerSubject)
                .map(projection -> new LinkedAccountView(
                        projection.getAccountId(),
                        toLinkedAccountStatus(projection.getStatus())
                ));
    }

    private LinkedAccountStatus toLinkedAccountStatus(AccountStatus status) {
        return switch (status) {
            case ACTIVE -> LinkedAccountStatus.ACTIVE;
            case INACTIVE -> LinkedAccountStatus.INACTIVE;
        };
    }
}
