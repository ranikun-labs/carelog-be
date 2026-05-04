package carelog.carelog.common.web.exception

import carelog.carelog.common.web.dto.response.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(CustomException::class)
    fun handleCustomException(e: CustomException): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("CustomException occurred: {}", e.message)
        val status = e.exceptionStatus
        return ResponseEntity(ApiResponse.of(status.httpStatus, status.message), status.httpStatus)
    }

    @ExceptionHandler(Exception::class)
    fun handleGlobalException(e: Exception): ResponseEntity<ApiResponse<Nothing>> {
        log.error("Unhandled exception occurred: {}", e.message, e)
        return ResponseEntity(
            ApiResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "서버에 예상치 못한 오류가 발생했습니다."),
            HttpStatus.INTERNAL_SERVER_ERROR,
        )
    }
}