package carelog.carelog.journal.web

import carelog.carelog.auth.app.CustomUserDetails
import carelog.carelog.common.web.dto.response.ApiResponse
import carelog.carelog.journal.app.JournalTemplateService
import carelog.carelog.journal.web.dto.JournalTemplateCreateRequest
import carelog.carelog.journal.web.dto.JournalTemplateResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/journal-templates")
class JournalTemplateController(
    private val journalTemplateService: JournalTemplateService,
) {
    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    fun createTemplate(
        @RequestBody request: JournalTemplateCreateRequest,
        @AuthenticationPrincipal userDetails: CustomUserDetails,
    ): ResponseEntity<ApiResponse<JournalTemplateResponse>> =
        ApiResponse.created(journalTemplateService.createTemplate(request, userDetails))

    @GetMapping
    @PreAuthorize("hasRole('MANAGER')")
    fun findAllTemplates(): ResponseEntity<ApiResponse<List<JournalTemplateResponse>>> =
        ApiResponse.ok(journalTemplateService.findAllTemplates())
}
