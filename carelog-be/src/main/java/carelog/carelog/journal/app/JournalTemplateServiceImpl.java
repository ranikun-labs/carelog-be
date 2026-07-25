package carelog.carelog.journal.app;

import carelog.carelog.auth.app.UserPrincipal;
import carelog.carelog.journal.domain.JournalTemplate;
import carelog.carelog.journal.domain.JournalTemplateRepository;
import carelog.carelog.journal.domain.JournalTemplateStatus;
import carelog.carelog.journal.web.dto.JournalTemplateCreateRequest;
import carelog.carelog.journal.web.dto.JournalTemplateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JournalTemplateServiceImpl implements JournalTemplateService {

    private final JournalTemplateRepository journalTemplateRepository;

    @Override
    @Transactional
    public JournalTemplateResponse createTemplate(JournalTemplateCreateRequest request, UserPrincipal userDetails) {
        JournalTemplate template = JournalTemplate.create(request.name(), request.fields());
        template.assignOrganization(userDetails.getOrganizationId());

        JournalTemplateResponse response = JournalTemplateResponse.from(journalTemplateRepository.save(template));
        return response;
    }

    @Override
    public List<JournalTemplateResponse> findAllTemplates() {
        List<JournalTemplateResponse> responses = journalTemplateRepository.findAllByStatus(JournalTemplateStatus.ACTIVE)
                .stream()
                .map(JournalTemplateResponse::from)
                .toList();
        return responses;
    }
}
