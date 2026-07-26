package carelog.carelog.auth.app;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class GatewayUserDetails implements UserDetails, UserPrincipal {

    // Identity Foundation B0부터 X-User-Id(=JWT subject)는 accountId다. Gateway는 loginId를 전달하지
    // 않으므로(애초에 모른다) getUserId()는 accountId의 문자열 표현을 그대로 반환한다.
    private final UUID accountId;
    private final UUID organizationId;
    private final String role;
    private final UUID publicId;

    public GatewayUserDetails(
            UUID accountId, UUID organizationId,
            String role, UUID publicId
    ) {
        this.accountId = accountId;
        this.organizationId = organizationId;
        this.role = role;
        this.publicId = publicId;
    }

    @Override
    public UUID getAccountId() { return accountId; }

    @Override
    public String getUserId() { return accountId.toString(); }

    @Override
    public UUID getOrganizationId() { return organizationId; }

    @Override
    public String getRole() { return role; }

    @Override
    public UUID getPublicId() { return publicId; }

    @Override
    public String getUsername() { return accountId.toString(); }

    @Override
    public String getPassword() { return null; }


    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
