package carelog.carelog.auth.app;

import java.util.UUID;

public interface UserPrincipal {
    String getUserId();
    UUID getOrganizationId();
    String getRole();
    UUID getPublicId();
}
