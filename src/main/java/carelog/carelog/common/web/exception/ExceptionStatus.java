package carelog.carelog.common.web.exception;

import lombok.*;
import org.springframework.http.*;

@Getter
@RequiredArgsConstructor
public enum ExceptionStatus {

    // 400 BAD_REQUEST
    INVALID_USER_ROLE(HttpStatus.BAD_REQUEST, "유효하지 않은 사용자 역할입니다."),

    // 404 NOT_FOUND
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    RELATION_NOT_FOUND(HttpStatus.NOT_FOUND, "관계를 찾을 수 없습니다."),

    // 409 CONFLICT
    DUPLICATE_USER_ID(HttpStatus.CONFLICT, "이미 존재하는 사용자 ID입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 존재하는 이메일입니다."),
    RELATION_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 존재하는 관계입니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
