package carelog.carelog.relation.web.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RelationCreateRequest {
    private Long managerId;
    private Long customerId;
}
