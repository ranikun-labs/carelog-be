package carelog.carelog.customer.web.dto;

import carelog.carelog.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record CustomerResponse(
        @Schema(description = "고객 공개 ID") UUID publicId,
        @Schema(description = "고객 표시 이름") String displayName,
        @Schema(description = "고객 메모") String customerMemo
) {
    public static CustomerResponse from(User user) {
        return new CustomerResponse(user.getPublicId(), user.getName(), user.getCustomerMemo());
    }
}
