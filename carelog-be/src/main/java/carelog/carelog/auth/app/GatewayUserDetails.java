package carelog.carelog.auth.app;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class GatewayUserDetails implements UserDetails, UserPrincipal {

    private final String userId;
    private final UUID organizationId;
    private final String role;
    private final UUID publicId;

    public GatewayUserDetails(
            String userId, UUID organizationId,
            String role, UUID publicId
    ) {
        this.userId = userId;
        this.organizationId = organizationId;
        this.role = role;
        this.publicId = publicId;
    }


    @Override public String getUserId() { return userId; }
    @Override public UUID getOrganizationId() { return
            organizationId; }
    @Override public String getRole() { return role; }
    @Override public UUID getPublicId() { return publicId;
    }

    @Override public String getUsername() { return userId;
    }
    @Override public String getPassword() { return null; }


    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
