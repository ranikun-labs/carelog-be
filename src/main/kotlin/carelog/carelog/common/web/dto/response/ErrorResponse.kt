package carelog.carelog.common.web.dto.response

import java.time.OffsetDateTime

data class ErrorResponse(
    val timestamp: OffsetDateTime,
    val status: Int,
    val error: String,
    val message: String,
    val path: String,
) {
    constructor(status: Int, error: String, message: String, path: String) :
        this(OffsetDateTime.now(), status, error, message, path)
}