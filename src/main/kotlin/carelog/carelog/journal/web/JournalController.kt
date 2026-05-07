package carelog.carelog.journal.web

import carelog.carelog.auth.app.CustomUserDetails
import carelog.carelog.common.web.dto.response.ApiResponse
import carelog.carelog.journal.app.JournalService
import carelog.carelog.journal.web.dto.JournalCreateRequest
import carelog.carelog.journal.web.dto.JournalResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/relations/{relationPublicId}/journals")
@PreAuthorize("hasRole('MANAGER')")
class JournalController(
    private val journalService: JournalService,
) {
    @PostMapping
    fun createJournal(
        @PathVariable relationPublicId: UUID,
        @RequestBody request: JournalCreateRequest,
        @AuthenticationPrincipal userDetails: CustomUserDetails,
    ): ResponseEntity<ApiResponse<JournalResponse>> =
        ApiResponse.created(journalService.createJournal(relationPublicId, request, userDetails))

    @PutMapping("/{journalPublicId}")
    fun updateJournal(
        @PathVariable relationPublicId: UUID,
        @PathVariable journalPublicId: UUID,
        @RequestBody request: JournalCreateRequest,
        @AuthenticationPrincipal userDetails: CustomUserDetails,
    ): ResponseEntity<ApiResponse<JournalResponse>> =
        ApiResponse.ok(journalService.updateJournal(relationPublicId, journalPublicId, request, userDetails))

    @GetMapping
    fun findAllJournals(
        @PathVariable relationPublicId: UUID,
        @AuthenticationPrincipal userDetails: CustomUserDetails,
    ): ResponseEntity<ApiResponse<List<JournalResponse>>> =
        ApiResponse.ok(journalService.findAllJournals(relationPublicId, userDetails))

    @GetMapping("/{journalPublicId}")
    fun findJournal(
        @PathVariable relationPublicId: UUID,
        @PathVariable journalPublicId: UUID,
        @AuthenticationPrincipal userDetails: CustomUserDetails,
    ): ResponseEntity<ApiResponse<JournalResponse>> =
        ApiResponse.ok(journalService.findJournal(relationPublicId, journalPublicId, userDetails))

    @GetMapping("/{journalPublicId}/history")
    fun findJournalHistory(
        @PathVariable relationPublicId: UUID,
        @PathVariable journalPublicId: UUID,
        @AuthenticationPrincipal userDetails: CustomUserDetails,
    ): ResponseEntity<ApiResponse<List<JournalResponse>>> =
        ApiResponse.ok(journalService.findJournalHistory(relationPublicId, journalPublicId, userDetails))
}
