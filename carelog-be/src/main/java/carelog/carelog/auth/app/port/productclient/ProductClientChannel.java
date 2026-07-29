package carelog.carelog.auth.app.port.productclient;

/** 제품 인증 Client의 채널이다. 실제 등록 여부와 별개로 제품 중립적으로 정의한다. */
public enum ProductClientChannel {
    WEB,
    IOS,
    ANDROID
}
