package carelog.carelog.common.web.dto.response

import java.time.OffsetDateTime

data class ErrorResponse(
    val timestamp: OffsetDateTime = OffsetDateTime.now(),
    val status: Int,
    val error: String,
    val message: String,
    val path: String
)
