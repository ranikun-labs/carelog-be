package carelog.carelog.journal.app;

import carelog.carelog.auth.app.CustomUserDetails;
import carelog.carelog.journal.web.dto.JournalCreateRequest;
import carelog.carelog.journal.web.dto.JournalResponse;

import java.util.List;
import java.util.UUID;

public interface JournalService {

    JournalResponse createJournal(UUID relationPublicId, JournalCreateRequest request, CustomUserDetails userDetails);
    JournalResponse updateJournal(UUID relationPublicId, UUID journalPublicId, JournalCreateRequest request, CustomUserDetails userDetails);
    List<JournalResponse> findAllJournals(UUID relationPublicId, CustomUserDetails userDetails);
    JournalResponse findJournal(UUID relationPublicId, UUID journalPublicId, CustomUserDetails userDetails);
    List<JournalResponse> findJournalHistory(UUID relationPublicId, UUID journalPublicId, CustomUserDetails userDetails);
}