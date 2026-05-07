package carelog.carelog.relation.domain

enum class RelationStatus(
    val key: String,
    val title: String,
) {
    ACTIVE("ACTIVE", "활성"),
    TERMINATED("TERMINATED", "종료"),
}
