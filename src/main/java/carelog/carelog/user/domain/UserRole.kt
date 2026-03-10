package carelog.carelog.user.domain

enum class UserRole(val key: String, val title: String) {
    ADMIN("ADMIN", "관리자"),
    MANAGER("MANAGER", "매니저"),
    CUSTOMER("CUSTOMER", "고객")
}