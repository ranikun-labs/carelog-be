package carelog.carelog.common.config;

import java.util.UUID;

public class TenantContext {
    private static final ThreadLocal<UUID> ORGANIZATION_ID = new ThreadLocal<>();

    public static void set(UUID organizationId) { ORGANIZATION_ID.set(organizationId); }
    public static UUID get() { return ORGANIZATION_ID.get(); }
    public static void clear() { ORGANIZATION_ID.remove(); }
}
