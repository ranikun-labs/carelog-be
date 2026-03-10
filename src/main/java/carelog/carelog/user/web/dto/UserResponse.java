package carelog.carelog.user.web.dto;

import carelog.carelog.user.domain.ManagerType;
import carelog.carelog.user.domain.User;
import carelog.carelog.user.domain.UserRole;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class UserResponse {
    private Long id;
    private UUID publicId;
    private String userId; // userId 필드 추가
    private String email;
    private String name;
    private UserRole role;
    private ManagerType managerType;
    private String phoneEncrypted;
    private String addressEncrypted;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .publicId(user.getPublicId())
                .userId(user.getUserId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole())
                .managerType(user.getManagerType())
                .phoneEncrypted(user.getPhoneEncrypted())
                .addressEncrypted(user.getAddressEncrypted())
                .build();
    }
}
