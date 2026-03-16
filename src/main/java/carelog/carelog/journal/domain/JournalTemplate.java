package carelog.carelog.journal.domain;

import carelog.carelog.common.domain.TenantBaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "journal_templates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JournalTemplate extends TenantBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "journal_templates_id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "public_id", unique = true,nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "name", nullable = false)
    private String name;

    @Type(JsonBinaryType.class)
    @Column(name = "fields", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> fields;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private JournalTemplateStatus status;

    public JournalTemplate(
            String name,
            List<Map<String, Object>> fields
    ) {
        this.publicId = UUID.randomUUID();
        this.name = name;
        this.fields = fields;
        this.status = JournalTemplateStatus.ACTIVE;
    }

    public static JournalTemplate create(
            String name,
            List<Map<String, Object>> fields
    ) {
        return new JournalTemplate(name, fields);
    }

    public void deactivate() {
        this.status = JournalTemplateStatus.INACTIVE;
    }
}
