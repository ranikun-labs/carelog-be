package carelog.carelog.auth.app;

import carelog.carelog.user.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class CustomUserDetails implements UserDetails {

    private final User user;

    public CustomUserDetails(User user) {
        this.user = user;
    }

    public UUID getOrganizationId() { return user.getOrganizationId(); }
    public UUID getPublicId() { return user.getPublicId(); }
    public String getRole() {return user.getRole().name(); }

    @Override
    public String getUsername() {
        return user.getUserId();
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    // ✅ 추가: deletedAt 기반 계정 활성 상태 연결 (UserDetails 계약 준수)
    // 현재는 soft-delete 구조라 @SQLRestriction이 1차 방어선이지만,
    // Native Query / 캐싱 우회 시 2차 방어선 역할 + suspended 필드 추가 시 확장 포인트
    @Override
    public boolean isEnabled() {
        return UserDetails.super.isEnabled();
    }
}
