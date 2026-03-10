package carelog.carelog.user.web.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerCreateRequest {

    @NotBlank(message = "이름은 필수 입력 값입니다.")
    private String name;
}
