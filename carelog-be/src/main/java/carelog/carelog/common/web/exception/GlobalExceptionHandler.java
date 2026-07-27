package carelog.carelog.common.web.exception;

import carelog.carelog.auth.app.port.oauth.OAuthStateStoreUnavailableException;
import carelog.carelog.common.web.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 우리가 직접 정의한 CustomException을 처리하는 핸들러
     */
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Object>> handleCustomException(CustomException e) {
        log.warn("CustomException occurred: {}", e.getMessage());
        ExceptionStatus status = e.getExceptionStatus();
        ApiResponse<Object> response = ApiResponse.of(status.getHttpStatus(), status.getMessage());
        return new ResponseEntity<>(response, status.getHttpStatus());
    }

    /**
     * OAuth state store 장애는 인증 실패가 아닌 일시적 인프라 장애로 응답한다.
     */
    @ExceptionHandler(OAuthStateStoreUnavailableException.class)
    public ResponseEntity<ApiResponse<Object>> handleOAuthStateStoreUnavailable(
            OAuthStateStoreUnavailableException e
    ) {
        ExceptionStatus status = ExceptionStatus.OAUTH_STATE_STORE_UNAVAILABLE;
        ApiResponse<Object> response = ApiResponse.of(status.getHttpStatus(), status.getMessage());
        return new ResponseEntity<>(response, status.getHttpStatus());
    }

    /**
     * 나머지 모든 예외를 처리하는 핸들러
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGlobalException(Exception e) {
        log.error("Unhandled exception occurred: {}", e.getMessage(), e);
        ApiResponse<Object> response = ApiResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "서버에 예상치 못한 오류가 발생했습니다.");
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
