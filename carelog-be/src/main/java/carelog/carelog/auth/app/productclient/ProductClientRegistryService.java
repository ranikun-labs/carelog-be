package carelog.carelog.auth.app.productclient;

import carelog.carelog.auth.app.port.productclient.ProductClientReader;
import carelog.carelog.auth.app.port.productclient.ProductClientRegistry;
import carelog.carelog.auth.app.port.productclient.RegisteredProductClient;
import carelog.carelog.auth.domain.ProductClient;
import carelog.carelog.auth.domain.ProductClientRepository;
import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** DB Registry에서 활성 Client만 OAuth Core에 전달한다. */
@Service
@RequiredArgsConstructor
public class ProductClientRegistryService implements ProductClientRegistry, ProductClientReader {

    private final ProductClientRepository productClientRepository;

    @Override
    public RegisteredProductClient readEnabled(String clientId) {
        return requireEnabled(clientId);
    }

    @Override
    public RegisteredProductClient requireEnabled(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new CustomException(ExceptionStatus.INVALID_PRODUCT_CLIENT_ID);
        }

        ProductClient client = productClientRepository.findByClientId(clientId)
                .orElseThrow(() -> new CustomException(ExceptionStatus.UNKNOWN_PRODUCT_CLIENT));
        if (!client.isEnabled()) {
            throw new CustomException(ExceptionStatus.DISABLED_PRODUCT_CLIENT);
        }

        return new RegisteredProductClient(client.getClientId(), client.getProduct(), client.getChannel());
    }
}
