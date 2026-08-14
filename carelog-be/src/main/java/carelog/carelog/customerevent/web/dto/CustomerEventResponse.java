package carelog.carelog.customerevent.web.dto;

import carelog.carelog.customerevent.domain.CustomerEvent;
import carelog.carelog.customerevent.domain.CustomerEventStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CustomerEventResponse(
        UUID id,
        UUID customerId,
        CustomerEventStatus status,
        OffsetDateTime scheduledAt,
        OffsetDateTime occurredAt,
        String descriptor,
        String note
) {
    public static CustomerEventResponse from(CustomerEvent event) {
        return new CustomerEventResponse(
                event.getPublicId(),
                event.getCustomer().getPublicId(),
                event.getStatus(),
                event.getScheduledAt(),
                event.getOccurredAt(),
                event.getDescriptor(),
                event.getNote());
    }
}
