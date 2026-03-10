package carelog.carelog.common.web.dto.response

import com.fasterxml.jackson.annotation.JsonInclude
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiResponse<T> (
    val status: Int,
    val message: String,
    val data: T? = null
) {
    companion object {
        // 데이터가 없는 성공 응답을 생성
        fun <T> of(status: HttpStatus, message: String): ApiResponse<T> =
            ApiResponse(status.value(),message)

        // 데이터가 있는 성공 응답을 생성
        fun <T> of(status: HttpStatus, message: String, data: T): ApiResponse<T> =
            ApiResponse(status.value(), message, data)

        // [정적 헬퍼 메서드] 성공 (200 OK) 응답 - 데이터 포함
        fun <T> ok(data: T): ResponseEntity<ApiResponse<T>> =
            ResponseEntity.ok(ApiResponse(
                HttpStatus.OK.value(), "요청에 성공하였습니다.",
                data
            ))

        // [정적 헬퍼 메서드] 성공 (201 Created) 응답 - 데이터 포함
        fun <T> created(data: T): ResponseEntity<ApiResponse<T>> =
            ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse(
                    HttpStatus.CREATED.value(),
                    "리소스를 성공적으로 생성하였습니다.",
                    data
                ))

        // [정적 헬퍼 메서드] 성공 (204 No Content) 응답 - 데이터 없음
        fun noContent(): ResponseEntity<Void> =
            ResponseEntity.noContent().build()
    }
}
