package carelog.carelog.common.web.exception


class CustomException(
    val exceptionStatus: ExceptionStatus
): RuntimeException(exceptionStatus.message)
