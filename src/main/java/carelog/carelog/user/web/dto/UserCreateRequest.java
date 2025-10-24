package carelog.carelog.user.web.dto;

import carelog.carelog.user.domain.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserCreateRequest {

    @NotBlank(message = "사용자의 ID는 필수 입력 값입니다.")
    @Size(min = 4, max = 20, message = "사용자 ID는 4자 이상 20이하로 입력해주세요.")
    private String userId;

    @NotBlank(message = "이메일은 필수 입력 값입니다.")
    @Email(message = "유효한 이메일 형식이 아닙니다.")
    private String email;

    @NotBlank(message = "비밀번호는 필수 입력 값입니다.")
    @Size(min = 8, max = 20, message = "비밀번호는 8자 이상 20자 이하로 입력해주세요.")
    private String password;

    @NotBlank(message = "이름 필수 입력 값입니다.")
    private String name;

    @NotNull(message = "역활은 필수 입력 값입니다.")
    private UserRole role;

    private String phoneEncrypted;
    private String addressEncrypted;
}
