package carelog.carelog.relation.web.dto;

import carelog.carelog.relation.domain.*;
import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@NoArgsConstructor
public class RelationStatusUpdateRequest {

    @NotNull(message = "상태는 필수 입력 값입니다.")
    private RelationStatus status;
}
