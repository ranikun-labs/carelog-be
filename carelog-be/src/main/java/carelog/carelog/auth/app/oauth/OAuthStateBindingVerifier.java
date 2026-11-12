package carelog.carelog.auth.app.oauth;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Component;

import carelog.carelog.auth.app.port.oauth.OAuthBoundProductClient;
import carelog.carelog.auth.app.port.oauth.OAuthStateRecord;
import carelog.carelog.auth.app.port.productclient.ProductClientReader;
import carelog.carelog.auth.app.port.productclient.RegisteredProductClient;
import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OAuthStateBindingVerifier {

    private final ProductClientReader productClientReader;
    private final Clock clock;

    public boolean verify(String expectedProvider, OAuthStateRecord state) {
        if (expectedProvider == null || state == null
            || state.version() != OAuthStateRecord.CURRENT_VERSION
            || !Objects.equals(expectedProvider, state.provider())
            || state.issuedAt() == null
            || state.expiresAt() == null
            || state.productClient() == null) {
            return false;
    }

    Instant now = clock.instant();
    if (state.issuedAt().isAfter(now) || !state.expiresAt().isAfter(now)) {
        return false;
    }

    OAuthBoundProductClient boundClient = state.productClient();
    if (boundClient.clientId() == null || boundClient.clientId().isBlank()
    || boundClient.product() == null
    || boundClient.channel() == null) {
        return false;
    }

    RegisteredProductClient registeredClient;
    try {
        registeredClient = productClientReader.requireEnabled(boundClient.clientId());
    } catch (CustomException e) {
        ExceptionStatus status = e.getExceptionStatus();
        if (status == ExceptionStatus.INVALID_PRODUCT_CLIENT_ID
            || status == ExceptionStatus.UNKNOWN_PRODUCT_CLIENT
            || status == ExceptionStatus.DISABLED_PRODUCT_CLIENT
        ) {
           return false ;
        }
        throw e;
    }

    return Objects.equals(boundClient.clientId(), registeredClient.clientId())
    && boundClient.product() == registeredClient.product()
    && boundClient.channel() == registeredClient.channel();
}
}
