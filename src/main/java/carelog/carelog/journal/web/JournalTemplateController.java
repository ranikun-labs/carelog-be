package carelog.carelog.journal.web;

import carelog.carelog.auth.app.CustomUserDetails;
import carelog.carelog.common.web.dto.response.ApiResponse;
import carelog.carelog.journal.app.JournalTemplateService;
import carelog.carelog.journal.web.dto.JournalTemplateCreateRequest;
import carelog.carelog.journal.web.dto.JournalTemplateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/journal-templates")
@RequiredArgsConstructor
public class JournalTemplateController {

    private final JournalTemplateService journalTemplateService;

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<JournalTemplateResponse>> createTemplate(
            @RequestBody JournalTemplateCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        JournalTemplateResponse response = journalTemplateService.createTemplate(request, userDetails);
        return ApiResponse.created(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<List<JournalTemplateResponse>>> findAllTemplates() {
        List<JournalTemplateResponse> responses = journalTemplateService.findAllTemplates();
        return ApiResponse.ok(responses);
    }
}
