package carelog.carelog.auth.app.port.productclient;

/** 활성 제품 인증 Client 조회 경계다. unknown·disabled client는 fail-closed로 거부한다. */
public interface ProductClientReader {

    RegisteredProductClient requireEnabled(String clientId);
}
