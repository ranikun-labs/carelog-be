package carelog.carelog.journal.app;

import carelog.carelog.auth.app.UserPrincipal;
import carelog.carelog.journal.web.dto.JournalCreateRequest;
import carelog.carelog.journal.web.dto.JournalResponse;

import java.util.List;
import java.util.UUID;

public interface JournalService {

    JournalResponse createJournal(UUID relationPublicId, JournalCreateRequest request, UserPrincipal userDetails);
    JournalResponse updateJournal(UUID relationPublicId, UUID journalPublicId, JournalCreateRequest request, UserPrincipal userDetails);
    List<JournalResponse> findAllJournals(UUID relationPublicId, UserPrincipal userDetails);
    JournalResponse findJournal(UUID relationPublicId, UUID journalPublicId, UserPrincipal userDetails);
    List<JournalResponse> findJournalHistory(UUID relationPublicId, UUID journalPublicId, UserPrincipal userDetails);
}