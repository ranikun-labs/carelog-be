package carelog.carelog.common.web.exception;

import lombok.*;
import org.springframework.http.*;

@Getter
@RequiredArgsConstructor
public enum ExceptionStatus {

        // 400 BAD_REQUEST
        INVALID_USER_ROLE(HttpStatus.BAD_REQUEST, "유효하지 않은 사용자 역할입니다."),
        INVALID_MANAGER_FIELDS(HttpStatus.BAD_REQUEST, "매니저는 아이디, 이메일, 비밀번호, 직군이 필수입니다."),


        // 404 NOT_FOUND
        USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
        RELATION_NOT_FOUND(HttpStatus.NOT_FOUND, "관계를 찾을 수 없습니다."),
        JOURNAL_NOT_FOUND(HttpStatus.NOT_FOUND, "일지를 찾을 수 없습니다."),
        JOURNAL_TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, "일지 양식을 찾을 수 없습니다."),

        // 409 CONFLICT
        DUPLICATE_USER_ID(HttpStatus.CONFLICT, "이미 존재하는 사용자 ID입니다."),
        DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 존재하는 이메일입니다."),
        RELATION_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 존재하는 관계입니다."),

        // 403 FORBIDDEN
        ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
        JOURNAL_DELETE_NOT_ALLOWED(HttpStatus.FORBIDDEN, "일지는 삭제할 수 없습니다."),

    // Auth
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 일치하지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 Refresh Token입니다."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "만료된 Refresh Token입니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "Refresh Token을 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
