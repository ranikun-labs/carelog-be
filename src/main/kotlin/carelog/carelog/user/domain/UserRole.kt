package carelog.carelog.user.domain

enum class UserRole(
    val key: String,
    val title: String,
) {
    MANAGER("MANAGER", "관리자"),
    CUSTOMER("CUSTOMER", "고객"),
}
