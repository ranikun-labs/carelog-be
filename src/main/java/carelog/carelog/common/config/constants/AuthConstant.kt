package carelog.carelog.common.config.constants

object AuthConstant {

    object Role {
        const val ADMIN = "ADMIN"
        const val MANAGER = "MANAGER"
        const val STAFF = "STAFF"
    }

    object Check {
        const val ADMIN = "hasRole('${Role.ADMIN}')"
        const val MANAGER = "hasAnyRole('${Role.ADMIN}', '${Role.MANAGER}')"
        const val ALL = "hasAnyRole('${Role.ADMIN}', '${Role.MANAGER}','${Role.STAFF}')"
    }
}
