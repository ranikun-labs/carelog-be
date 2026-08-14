package carelog.carelog.customerevent.web.dto;

import java.util.UUID;

public record CustomerEventCreateRequest(
        UUID customerId,
        String status,
        String scheduledAt,
        String occurredAt,
        String descriptor,
        String note
) {
}
