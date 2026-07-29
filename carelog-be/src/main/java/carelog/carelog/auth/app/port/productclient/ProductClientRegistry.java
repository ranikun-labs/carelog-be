package carelog.carelog.auth.app.port.productclient;

/** Product Client Registry의 application 경계다. */
public interface ProductClientRegistry {

    RegisteredProductClient requireEnabled(String clientId);
}
