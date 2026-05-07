package carelog.carelog.journal.app

import carelog.carelog.auth.app.CustomUserDetails
import carelog.carelog.journal.web.dto.JournalCreateRequest
import carelog.carelog.journal.web.dto.JournalResponse
import java.util.UUID

interface JournalService {
    fun createJournal(relationPublicId: UUID, request: JournalCreateRequest, userDetails: CustomUserDetails): JournalResponse
    fun updateJournal(relationPublicId: UUID, journalPublicId: UUID, request: JournalCreateRequest, userDetails: CustomUserDetails): JournalResponse
    fun findAllJournals(relationPublicId: UUID, userDetails: CustomUserDetails): List<JournalResponse>
    fun findJournal(relationPublicId: UUID, journalPublicId: UUID, userDetails: CustomUserDetails): JournalResponse
    fun findJournalHistory(relationPublicId: UUID, journalPublicId: UUID, userDetails: CustomUserDetails): List<JournalResponse>
}
