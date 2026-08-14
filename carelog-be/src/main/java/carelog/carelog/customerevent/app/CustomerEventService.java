package carelog.carelog.customerevent.app;

import carelog.carelog.customerevent.web.dto.CustomerEventCreateRequest;
import carelog.carelog.customerevent.web.dto.CustomerEventResponse;
import carelog.carelog.customerevent.web.dto.CustomerEventUpdateRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface CustomerEventService {

    CustomerEventResponse create(UUID organizationId, CustomerEventCreateRequest request);

    CustomerEventResponse findById(UUID organizationId, UUID eventId);

    List<CustomerEventResponse> findAll(
            UUID organizationId, UUID customerId, OffsetDateTime from, OffsetDateTime to, int limit);

    CustomerEventResponse update(
            UUID organizationId, UUID eventId, CustomerEventUpdateRequest request);

    CustomerEventResponse occur(UUID organizationId, UUID eventId, OffsetDateTime occurredAt);

    CustomerEventResponse cancel(UUID organizationId, UUID eventId);
}
