package carelog.carelog.relation.web.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RelationCreateRequest {

    @NotNull(message = "매니저ID 는 필수 입력 값입니다.")
    private Long managerId;

    @NotNull(message = "고객ID 는 필수 입력 값입니다.")
    private Long customerId;
}
