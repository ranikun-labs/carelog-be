package carelog.carelog.journal.domain

import carelog.carelog.common.domain.TenantBaseEntity
import carelog.carelog.relation.domain.Relation
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "relation_journals")
class RelationJournal private constructor(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "relation_id", nullable = false)
    val relation: Relation,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    val template: JournalTemplate?,

    @Column(name = "title", nullable = false)
    val title: String,

    @Column(name = "visit_date", nullable = false)
    val visitDate: LocalDate,

    @Type(JsonBinaryType::class)
    @Column(name = "case_data", columnDefinition = "jsonb", nullable = false)
    val caseData: Map<String, Any>,

    @Type(JsonBinaryType::class)
    @Column(name = "private_data", columnDefinition = "jsonb")
    val privateData: Map<String, Any>?,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: JournalStatus,

    @Column(name = "previous_id")
    val previousId: Long?,
) : TenantBaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "relation_journals_id", updatable = false, nullable = false)
    val id: Long = 0

    @Column(name = "public_id", unique = true, nullable = false, updatable = false)
    val publicId: UUID = UUID.randomUUID()

    fun supersede() { status = JournalStatus.SUPERSEDED }

    companion object {
        fun create(
            relation: Relation, template: JournalTemplate?,
            title: String, visitDate: LocalDate,
            caseData: Map<String, Any>, privateData: Map<String, Any>?,
        ) = RelationJournal(relation, template, title, visitDate, caseData, privateData, JournalStatus.ACTIVE, null)

        fun createAsRevision(
            relation: Relation, template: JournalTemplate?,
            title: String, visitDate: LocalDate,
            caseData: Map<String, Any>, privateData: Map<String, Any>?,
            previousId: Long,
        ) = RelationJournal(relation, template, title, visitDate, caseData, privateData, JournalStatus.ACTIVE, previousId)
    }
}
