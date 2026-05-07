package carelog.carelog.journal.domain

import carelog.carelog.common.domain.TenantBaseEntity
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import java.util.UUID

@Entity
@Table(name = "journal_templates")
class JournalTemplate private constructor(
    @Column(name = "name", nullable = false)
    val name: String,

    @Type(JsonBinaryType::class)
    @Column(name = "fields", columnDefinition = "jsonb", nullable = false)
    val fields: List<Map<String, Any>>,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: JournalTemplateStatus,
) : TenantBaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "journal_templates_id", updatable = false, nullable = false)
    val id: Long = 0

    @Column(name = "public_id", unique = true, nullable = false, updatable = false)
    val publicId: UUID = UUID.randomUUID()

    fun deactivate() { status = JournalTemplateStatus.INACTIVE }

    companion object {
        fun create(name: String, fields: List<Map<String, Any>>) =
            JournalTemplate(name, fields, JournalTemplateStatus.ACTIVE)
    }
}
