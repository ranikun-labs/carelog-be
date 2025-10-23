
package carelog.carelog.web.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL) // Null인 필드는 JSON 변환 시 제외
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final String message; // 성공 메시지 또는 추가 정보

    private ApiResponse(boolean success, T data, String message) {
        this.success = success;
        this.data = data;
        this.message = message;
    }

    // 성공 응답 (데이터만 포함)
    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(true, data, null);
    }

    // 성공 응답 (데이터와 메시지 포함)
    public static <T> ApiResponse<T> of(T data, String message) {
        return new ApiResponse<>(true, data, message);
    }

    // 성공 응답 (데이터 없이 메시지만 포함)
    public static <T> ApiResponse<T> ok(String message) {
        return new ApiResponse<>(true, null, message);
    }
}
