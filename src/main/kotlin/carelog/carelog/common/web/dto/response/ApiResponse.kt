package carelog.carelog.common.web.dto.response

import com.fasterxml.jackson.annotation.JsonInclude
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

@JsonInclude(JsonInclude.Include.NON_NULL)
class ApiResponse<T> private constructor(
    val status: Int,
    val message: String,
    val data: T?,
) {
    companion object {
        @JvmStatic
        fun <T> of(status: HttpStatus, message: String): ApiResponse<T> =
            ApiResponse(status.value(), message, null)

        @JvmStatic
        fun <T> of(status: HttpStatus, message: String, data: T): ApiResponse<T> =
            ApiResponse(status.value(), message, data)

        @JvmStatic
        fun <T> ok(data: T): ResponseEntity<ApiResponse<T>> =
            ResponseEntity.ok(ApiResponse(HttpStatus.OK.value(), "요청에 성공하였습니다.", data))

        @JvmStatic
        fun <T> created(data: T): ResponseEntity<ApiResponse<T>> =
            ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse(HttpStatus.CREATED.value(), "리소스를 성공적으로 생성하였습니다.", data))

        @JvmStatic
        fun noContent(): ResponseEntity<Void> = ResponseEntity.noContent().build()
    }
}