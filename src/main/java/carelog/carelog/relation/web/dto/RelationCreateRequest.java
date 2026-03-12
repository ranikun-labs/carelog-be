package carelog.carelog.relation.web.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class RelationCreateRequest {

    @NotNull(message = "고객 PublicId는 필수 입력 값입니다.")
    private UUID customerPublicId;
}