package carelog.carelog.journal.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JournalTemplateRepository extends JpaRepository<JournalTemplate, Long> {

    Optional<JournalTemplate> findByPublicId(UUID publicId);
    List<JournalTemplate> findAllByStatus(JournalTemplateStatus status);
}
