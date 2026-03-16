package carelog.carelog.journal.domain;

import carelog.carelog.common.domain.TenantBaseEntity;
import carelog.carelog.relation.domain.Relation;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

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

    @Type(JsonBinaryType.class)
    @Column(name = "content", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private JournalStatus status;

    @Column(name = "previous_id")
    private Long previousId;


    public RelationJournal(
            Relation relation, JournalTemplate template,
            Map<String, Object> content, Long previousId
    ) {
        this.publicId = UUID.randomUUID();
        this.relation = relation;
        this.template = template;
        this.content = content;
        this.status = JournalStatus.ACTIVE;
        this.previousId = previousId;
    }

    // 최초 진료 일지 생성
    public static RelationJournal create(
            Relation relation, JournalTemplate template,
            Map<String, Object> content
    ) {
        return new RelationJournal(relation, template, content, null);
    }

    // 기존 일지의 수정본 생성 (이전 버전 id 연결)
    public static RelationJournal createAsRevision(
            Relation relation, JournalTemplate template,
            Map<String, Object> content, Long previousId
    ) {
        return new RelationJournal(relation, template, content, previousId);
    }

    // 이전 버전을 대체됨 상태로 변경
    public void supersede() {
        this.status = JournalStatus.SUPERSEDED;
    }

}
