package carelog.carelog.common.web.exception

import org.springframework.http.HttpStatus

/**
 * 에러 코드 공통 인터페이스
 * - 모든 도메인별 ErrorCode enum이 구현해야 함
 * - CustomException에서 사용
 */
interface ErrorCode {
    val httpStatus: HttpStatus
    val message: String
}