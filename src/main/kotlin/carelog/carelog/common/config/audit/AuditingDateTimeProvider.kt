package carelog.carelog.common.config.audit

import org.springframework.data.auditing.DateTimeProvider
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.time.temporal.TemporalAccessor
import java.util.Optional

@Component("auditingDateTimeProvider")
class AuditingDateTimeProvider : DateTimeProvider {

    override fun getNow(): Optional<TemporalAccessor> = Optional.of(OffsetDateTime.now())
}