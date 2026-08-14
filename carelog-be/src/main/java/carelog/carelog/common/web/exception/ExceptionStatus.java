package carelog.carelog.common.web.exception;

import lombok.*;
import org.springframework.http.*;

@Getter
@RequiredArgsConstructor
public enum ExceptionStatus {

        // 400 BAD_REQUEST
        INVALID_USER_ROLE(HttpStatus.BAD_REQUEST, "유효하지 않은 사용자 역할입니다."),
        INVALID_MANAGER_FIELDS(HttpStatus.BAD_REQUEST, "매니저는 아이디, 이메일, 비밀번호, 직군이 필수입니다."),
        UNSUPPORTED_OAUTH_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 OAuth 제공자입니다."),
        UNSUPPORTED_CLIENT_CHANNEL(HttpStatus.BAD_REQUEST, "지원하지 않는 OAuth 클라이언트 채널입니다."),
        INVALID_PRODUCT_CLIENT_ID(HttpStatus.BAD_REQUEST, "유효하지 않은 제품 인증 Client입니다."),
        INVALID_PRODUCT_CLIENT_CHANNEL_MAPPING(HttpStatus.BAD_REQUEST, "제품 인증 Client와 채널 조합이 유효하지 않습니다."),
        INVALID_OAUTH_RETURN_TO(HttpStatus.BAD_REQUEST, "유효하지 않은 로그인 완료 후 이동 경로입니다."),
        INVALID_CUSTOMER_DISPLAY_NAME(HttpStatus.BAD_REQUEST, "고객 표시 이름은 필수이며 공백만 입력할 수 없습니다."),
        INVALID_CUSTOMER_EVENT(HttpStatus.BAD_REQUEST, "고객 이벤트 요청이 유효하지 않습니다."),


        // 404 NOT_FOUND
        USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
        CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "고객을 찾을 수 없습니다."),
        CUSTOMER_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "고객 이벤트를 찾을 수 없습니다."),
        RELATION_NOT_FOUND(HttpStatus.NOT_FOUND, "관계를 찾을 수 없습니다."),
        JOURNAL_NOT_FOUND(HttpStatus.NOT_FOUND, "일지를 찾을 수 없습니다."),
        JOURNAL_TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, "일지 양식을 찾을 수 없습니다."),

        // 409 CONFLICT
        DUPLICATE_USER_ID(HttpStatus.CONFLICT, "이미 존재하는 사용자 ID입니다."),
        DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 존재하는 이메일입니다."),
        RELATION_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 존재하는 관계입니다."),
        OAUTH_ACCOUNT_NOT_LINKED(HttpStatus.CONFLICT, "연결된 Carelog 계정이 없습니다."),
        OAUTH_IDENTITY_CONFLICT(HttpStatus.CONFLICT, "OAuth 계정 연결 상태가 유효하지 않습니다."),
        INVALID_CUSTOMER_EVENT_TRANSITION(HttpStatus.CONFLICT, "허용되지 않는 고객 이벤트 상태 전이입니다."),

        // 403 FORBIDDEN
        DISABLED_PRODUCT_CLIENT(HttpStatus.FORBIDDEN, "비활성화된 제품 인증 Client입니다."),

        // 403 FORBIDDEN
        ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
        JOURNAL_DELETE_NOT_ALLOWED(HttpStatus.FORBIDDEN, "일지는 삭제할 수 없습니다."),

    // Auth
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 일치하지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 Refresh Token입니다."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "만료된 Refresh Token입니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "Refresh Token을 찾을 수 없습니다."),
    INVALID_OAUTH_STATE(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 인증 요청입니다."),
    UNKNOWN_PRODUCT_CLIENT(HttpStatus.UNAUTHORIZED, "알 수 없는 제품 인증 Client입니다."),
    OAUTH_PROVIDER_AUTH_FAILED(HttpStatus.UNAUTHORIZED, "OAuth 인증에 실패했습니다."),
    OAUTH_STATE_STORE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "OAuth 인증 요청 저장소를 사용할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
