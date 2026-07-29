package carelog.carelog.auth.domain;

/** 제품 인증 Client가 허용하는 OAuth 요청 채널이다. */
public enum ProductClientChannel {
    WEB,
    /**
     * 기존 공개 OAuth API의 generic mobile authorization 계약 호환용 채널이다.
     * 실제 iOS/Android Product Client 등록을 의미하지 않는다.
     */
    MOBILE,
    /** 향후 명시적 clientId 계약에서 사용할 iOS 채널이다. */
    IOS,
    /** 향후 명시적 clientId 계약에서 사용할 Android 채널이다. */
    ANDROID
}
