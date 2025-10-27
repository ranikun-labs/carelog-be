package carelog.carelog.common.web.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ExceptionStatus {

    // 404 NOT_FOUND
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),

    // 409 CONFLICT
    DUPLICATE_USER_ID(HttpStatus.CONFLICT, "이미 존재하는 사용자 ID입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 존재하는 이메일입니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
