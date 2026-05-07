package carelog.carelog.journal.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface JournalTemplateRepository : JpaRepository<JournalTemplate, Long> {
    fun findByPublicId(publicId: UUID): Optional<JournalTemplate>
    fun findAllByStatus(status: JournalTemplateStatus): List<JournalTemplate>
}
