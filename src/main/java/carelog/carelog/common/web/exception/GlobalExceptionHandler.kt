package carelog.carelog.common.web.exception

import carelog.carelog.common.web.dto.response.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 우리가 직접 정의한 CustomException을 처리하는 핸들러
     */
    @ExceptionHandler(CustomException::class)
    fun handleCustomException(e: CustomException): ResponseEntity<ApiResponse<Unit>> {
        log.warn("CustomException occurred: {}", e.message);
        val status = e.exceptionStatus
        val body = ApiResponse.of<Unit>(status.httpStatus, status.message)
        return ResponseEntity(body, status.httpStatus)
    }

    /**
     * 나머지 모든 예외를 처리하는 핸들러
     */
    @ExceptionHandler(Exception::class)
    fun handleGlobalException(e: Exception): ResponseEntity<ApiResponse<Unit>> {
        log.error("Unhandled exception occurred: {}", e.message, e);
        val body = ApiResponse.of<Unit>(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "서버에 예상치 못한 오류가 발생했습니다."
        )

        return ResponseEntity(body, HttpStatus.INTERNAL_SERVER_ERROR)
    }
}
