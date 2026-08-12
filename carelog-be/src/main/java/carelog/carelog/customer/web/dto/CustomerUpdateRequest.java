package carelog.carelog.customer.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomerUpdateRequest(
        @Schema(description = "고객 표시 이름. 생략하면 기존 값을 유지합니다.")
        String displayName,

        @Schema(description = "고객 메모. 생략하면 기존 값을 유지합니다.")
        String customerMemo
) {
}
