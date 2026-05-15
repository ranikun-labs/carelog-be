package carelog.carelog.journal.web;

import carelog.carelog.auth.app.UserPrincipal;
import carelog.carelog.common.web.dto.response.ApiResponse;
import carelog.carelog.journal.app.JournalService;
import carelog.carelog.journal.web.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/relations/{relationPublicId}/journals")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MANAGER')")
public class JournalController {

    private final JournalService journalService;

    @PostMapping
    public ResponseEntity<ApiResponse<JournalResponse>> createJournal(
            @PathVariable UUID relationPublicId,
            @RequestBody JournalCreateRequest request,
            @AuthenticationPrincipal UserPrincipal userDetails
    ) {
        JournalResponse response = journalService.createJournal(relationPublicId, request, userDetails);
        return ApiResponse.created(response);
    }

    @PutMapping("/{journalPublicId}")
    public ResponseEntity<ApiResponse<JournalResponse>> updateJournal(
            @PathVariable UUID relationPublicId,
            @PathVariable UUID journalPublicId,
            @RequestBody JournalCreateRequest request,
            @AuthenticationPrincipal UserPrincipal userDetails
    ) {
        JournalResponse response = journalService.updateJournal(relationPublicId, journalPublicId, request, userDetails);
        return ApiResponse.ok(response);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<JournalResponse>>> findAllJournals(
            @PathVariable UUID relationPublicId,
            @AuthenticationPrincipal UserPrincipal userDetails
    ) {
        List<JournalResponse> responses = journalService.findAllJournals(relationPublicId, userDetails);
        return ApiResponse.ok(responses);
    }

    @GetMapping("/{journalPublicId}")
    public ResponseEntity<ApiResponse<JournalResponse>> findJournal(
            @PathVariable UUID relationPublicId,
            @PathVariable UUID journalPublicId,
            @AuthenticationPrincipal UserPrincipal userDetails
    ) {
        JournalResponse response = journalService.findJournal(relationPublicId, journalPublicId, userDetails);
        return ApiResponse.ok(response);
    }

    @GetMapping("/{journalPublicId}/history")
    public ResponseEntity<ApiResponse<List<JournalResponse>>> findJournalHistory(
            @PathVariable UUID relationPublicId,
            @PathVariable UUID journalPublicId,
            @AuthenticationPrincipal UserPrincipal userDetails
    ) {
        List<JournalResponse> responses = journalService.findJournalHistory(relationPublicId, journalPublicId, userDetails);
        return ApiResponse.ok(responses);
    }
}
