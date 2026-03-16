package carelog.carelog.journal.app;

import carelog.carelog.auth.app.CustomUserDetails;
import carelog.carelog.journal.web.dto.JournalTemplateCreateRequest;
import carelog.carelog.journal.web.dto.JournalTemplateResponse;

import java.util.List;

public interface JournalTemplateService {

    JournalTemplateResponse createTemplate(JournalTemplateCreateRequest request, CustomUserDetails userDetails);
    List<JournalTemplateResponse> findAllTemplates();
}
