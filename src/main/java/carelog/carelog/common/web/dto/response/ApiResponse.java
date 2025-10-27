package carelog.carelog.common.web.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final int status;       // HTTP 상태 코드
    private final String message;   // 응답 메시지
    private final T data;           // 응답 데이터

    private ApiResponse(HttpStatus status, String message, T data) {
        this.status = status.value();
        this.message = message;
        this.data = data;
    }

    // 데이터가 없는 성공 응답을 생성
    public static <T> ApiResponse<T> of(HttpStatus status, String message) {
        return new ApiResponse<>(status, message, null);
    }

    // 데이터가 있는 성공 응답을 생성
    public static <T> ApiResponse<T> of(HttpStatus status, String message, T data) {
        return new ApiResponse<>(status, message, data);
    }

    // [정적 헬퍼 메서드] 성공 (200 OK) 응답 - 데이터 포함
    public static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity
                .ok(new ApiResponse<>(HttpStatus.OK, "요청에 성공하였습니다.", data));
    }

    // [정적 헬퍼 메서드] 성공 (201 Created) 응답 - 데이터 포함
    public static <T> ResponseEntity<ApiResponse<T>> created(T data) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiResponse<>(HttpStatus.CREATED, "리소스를 성공적으로 생성하였습니다.", data));
    }

    // [정적 헬퍼 메서드] 성공 (204 No Content) 응답 - 데이터 없음
    public static ResponseEntity<Void> noContent() {
        return ResponseEntity.noContent().build();
    }
}
