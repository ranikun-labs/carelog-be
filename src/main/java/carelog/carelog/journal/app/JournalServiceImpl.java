package carelog.carelog.journal.app;

import carelog.carelog.auth.app.CustomUserDetails;
import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import carelog.carelog.journal.domain.*;
import carelog.carelog.journal.web.dto.JournalCreateRequest;
import carelog.carelog.journal.web.dto.JournalResponse;
import carelog.carelog.relation.domain.Relation;
import carelog.carelog.relation.domain.RelationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JournalServiceImpl implements JournalService {

    private final RelationJournalRepository journalRepository;
    private final JournalTemplateRepository templateRepository;
    private final RelationRepository relationRepository;

    private Relation findRelationAndCheckOwnership(UUID relationPublicId, CustomUserDetails userDetails) {
        Relation relation = relationRepository.findByPublicId(relationPublicId)
                .orElseThrow(() -> new CustomException(ExceptionStatus.RELATION_NOT_FOUND));

        if (!userDetails.getPublicId().equals(relation.getManager().getPublicId())) {
            throw new CustomException(ExceptionStatus.ACCESS_DENIED);
        }
        return relation;
    }

    @Override
    @Transactional
    public JournalResponse createJournal(UUID relationPublicId, JournalCreateRequest request, CustomUserDetails userDetails) {
        Relation relation = findRelationAndCheckOwnership(relationPublicId, userDetails);

        JournalTemplate template = null;
        if (request.templatePublicId() != null) {
            template = templateRepository.findByPublicId(request.templatePublicId())
                    .orElseThrow(() -> new CustomException(ExceptionStatus.JOURNAL_TEMPLATE_NOT_FOUND));
        }

        RelationJournal journal = RelationJournal.create(relation, template, request.content());
        journal.assignOrganization(userDetails.getOrganizationId());

        JournalResponse response = JournalResponse.from(journalRepository.save(journal));
        return response;
    }

    @Override
    @Transactional
    public JournalResponse updateJournal(UUID relationPublicId, UUID journalPublicId, JournalCreateRequest request, CustomUserDetails userDetails) {
        findRelationAndCheckOwnership(relationPublicId, userDetails);

        RelationJournal existing = journalRepository.findByPublicIdAndStatus(journalPublicId, JournalStatus.ACTIVE)
                .orElseThrow(() -> new CustomException(ExceptionStatus.JOURNAL_NOT_FOUND));

        JournalTemplate template = null;
        if (request.templatePublicId() != null) {
            template = templateRepository.findByPublicId(request.templatePublicId())
                    .orElseThrow(() -> new CustomException(ExceptionStatus.JOURNAL_TEMPLATE_NOT_FOUND));
        }

        existing.supersede();

        RelationJournal revision = RelationJournal.createAsRevision(
                existing.getRelation(), template, request.content(), existing.getId()
        );
        revision.assignOrganization(userDetails.getOrganizationId());

        JournalResponse response = JournalResponse.from(journalRepository.save(revision));
        return response;
    }

    @Override
    public List<JournalResponse> findAllJournals(UUID relationPublicId, CustomUserDetails userDetails) {
        Relation relation = findRelationAndCheckOwnership(relationPublicId, userDetails);

        List<JournalResponse> responses = journalRepository
                .findAllByRelationAndStatus(relation, JournalStatus.ACTIVE)
                .stream()
                .map(JournalResponse::from)
                .toList();
        return responses;
    }

    @Override
    public JournalResponse findJournal(UUID relationPublicId, UUID journalPublicId, CustomUserDetails userDetails) {
        findRelationAndCheckOwnership(relationPublicId, userDetails);

        RelationJournal journal = journalRepository.findByPublicIdAndStatus(journalPublicId, JournalStatus.ACTIVE)
                .orElseThrow(() -> new CustomException(ExceptionStatus.JOURNAL_NOT_FOUND));

        JournalResponse response = JournalResponse.from(journal);
        return response;
    }

    @Override
    public List<JournalResponse> findJournalHistory(UUID relationPublicId, UUID journalPublicId, CustomUserDetails userDetails) {
        findRelationAndCheckOwnership(relationPublicId, userDetails);

        RelationJournal latest = journalRepository.findByPublicIdAndStatus(journalPublicId, JournalStatus.ACTIVE)
                .orElseThrow(() -> new CustomException(ExceptionStatus.JOURNAL_NOT_FOUND));

        // previousId 체인 추적 — MVP 단계, 병목 측정 후 Recursive CTE로 전환
        List<RelationJournal> history = new ArrayList<>();
        RelationJournal current = latest;
        while (current != null) {
            history.add(current);
            current = current.getPreviousId() != null
                    ? journalRepository.findById(current.getPreviousId()).orElse(null)
                    : null;
        }

        List<JournalResponse> responses = history.stream()
                .map(JournalResponse::from)
                .toList();
        return responses;
    }
}
