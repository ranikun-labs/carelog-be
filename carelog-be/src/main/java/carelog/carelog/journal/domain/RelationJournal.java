package carelog.carelog.journal.domain;

import carelog.carelog.common.domain.TenantBaseEntity;
import carelog.carelog.relation.domain.Relation;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "relation_journals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RelationJournal extends TenantBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "relation_journals_id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "public_id", unique = true, nullable = false, updatable = false)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "relation_id", nullable = false)
    private Relation relation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private JournalTemplate template;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Type(JsonBinaryType.class)
    @Column(name = "case_data", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> caseData;

    @Type(JsonBinaryType.class)
    @Column(name = "private_data", columnDefinition = "jsonb")
    private Map<String, Object> privateData; // 내부 전용 PII — AI 파이프라인 진입 불가

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private JournalStatus status;

    @Column(name = "previous_id")
    private Long previousId;


    public RelationJournal(
            Relation relation, JournalTemplate template,
            String title, LocalDate visitDate,
            Map<String, Object> caseData,  Map<String, Object> privateData,
            Long previousId
    ) {
        this.publicId = UUID.randomUUID();
        this.relation = relation;
        this.template = template;
        this.title = title;
        this.visitDate = visitDate;
        this.caseData = caseData;
        this.privateData = privateData;
        this.status = JournalStatus.ACTIVE;
        this.previousId = previousId;
    }

    // 최초 진료 일지 생성
    public static RelationJournal create(
            Relation relation, JournalTemplate template,
            String title, LocalDate visitDate,
            Map<String, Object> caseData, Map<String, Object> privateData
    ) {
        return new RelationJournal(relation, template, title, visitDate,
                caseData, privateData, null);
    }

    // 기존 일지의 수정본 생성 (이전 버전 id 연결)
    public static RelationJournal createAsRevision(
            Relation relation, JournalTemplate template,
            String title, LocalDate visitDate,
            Map<String, Object> caseData, Map<String, Object> privateData,
            Long previousId
    ) {
        return new RelationJournal(relation, template, title, visitDate,
                caseData, privateData, previousId);
    }

    // 이전 버전을 대체됨 상태로 변경
    public void supersede() {
        this.status = JournalStatus.SUPERSEDED;
    }

}
