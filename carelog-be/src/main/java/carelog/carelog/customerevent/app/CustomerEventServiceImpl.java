package carelog.carelog.customerevent.app;

import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import carelog.carelog.customerevent.domain.CustomerEvent;
import carelog.carelog.customerevent.domain.CustomerEventRepository;
import carelog.carelog.customerevent.domain.CustomerEventStatus;
import carelog.carelog.customerevent.web.dto.CustomerEventCreateRequest;
import carelog.carelog.customerevent.web.dto.CustomerEventResponse;
import carelog.carelog.customerevent.web.dto.CustomerEventUpdateRequest;
import carelog.carelog.user.domain.User;
import carelog.carelog.user.domain.UserRepository;
import carelog.carelog.user.domain.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerEventServiceImpl implements CustomerEventService {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;

    private final CustomerEventRepository customerEventRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public CustomerEventResponse create(UUID organizationId, CustomerEventCreateRequest request) {
        requireOrganizationId(organizationId);
        if (request == null || request.customerId() == null) {
            throw invalidEvent();
        }

        User customer = findCustomer(organizationId, request.customerId());
        CustomerEventStatus status = parseStatus(request.status());
        CustomerEvent event = switch (status) {
            case PLANNED -> {
                if (request.occurredAt() != null) {
                    throw invalidEvent();
                }
                yield CustomerEvent.createPlanned(
                        customer, parseRequiredTime(request.scheduledAt()),
                        request.descriptor(), request.note());
            }
            case OCCURRED -> {
                if (request.scheduledAt() != null) {
                    throw invalidEvent();
                }
                yield CustomerEvent.createOccurred(
                        customer, parseRequiredTime(request.occurredAt()),
                        request.descriptor(), request.note());
            }
            case CANCELLED -> throw invalidEvent();
        };
        event.assignOrganization(organizationId);
        return CustomerEventResponse.from(customerEventRepository.save(event));
    }

    @Override
    public CustomerEventResponse findById(UUID organizationId, UUID eventId) {
        return CustomerEventResponse.from(findEvent(organizationId, eventId));
    }

    @Override
    public List<CustomerEventResponse> findAll(
            UUID organizationId, UUID customerId, OffsetDateTime from, OffsetDateTime to, int limit) {
        requireOrganizationId(organizationId);
        requireLimit(limit);
        if ((from == null) != (to == null)) {
            throw invalidEvent();
        }

        List<CustomerEvent> events;
        if (from != null) {
            if (!from.isBefore(to)) {
                throw invalidEvent();
            }
            if (customerId != null) {
                findCustomer(organizationId, customerId);
                events = customerEventRepository.findCustomerSchedule(
                        organizationId, customerId, from, to, PageRequest.of(0, limit));
            } else {
                events = customerEventRepository.findSchedule(
                        organizationId, from, to, PageRequest.of(0, limit));
            }
        } else if (customerId != null) {
            findCustomer(organizationId, customerId);
            events = customerEventRepository.findCustomerHistory(
                    organizationId, customerId, PageRequest.of(0, limit));
        } else {
            throw invalidEvent();
        }

        return events.stream().map(CustomerEventResponse::from).toList();
    }

    @Override
    @Transactional
    public CustomerEventResponse update(
            UUID organizationId, UUID eventId, CustomerEventUpdateRequest request) {
        CustomerEvent event = findEventForUpdate(organizationId, eventId);
        if (request == null) {
            throw invalidEvent();
        }

        if (request.descriptorPresent()) {
            event.updateDescriptor(request.descriptor());
        }
        if (request.notePresent()) {
            event.updateNote(request.note());
        }
        if (request.scheduledAtPresent()) {
            event.reschedule(parseRequiredTime(request.scheduledAt()));
        }
        if (request.occurredAtPresent()) {
            event.updateOccurredAt(parseRequiredTime(request.occurredAt()));
        }
        return CustomerEventResponse.from(event);
    }

    @Override
    @Transactional
    public CustomerEventResponse occur(UUID organizationId, UUID eventId, OffsetDateTime occurredAt) {
        CustomerEvent event = findEventForUpdate(organizationId, eventId);
        event.occur(occurredAt);
        return CustomerEventResponse.from(event);
    }

    @Override
    @Transactional
    public CustomerEventResponse cancel(UUID organizationId, UUID eventId) {
        CustomerEvent event = findEventForUpdate(organizationId, eventId);
        event.cancel();
        return CustomerEventResponse.from(event);
    }

    private CustomerEvent findEvent(UUID organizationId, UUID eventId) {
        if (organizationId == null || eventId == null) {
            throw eventNotFound();
        }
        return customerEventRepository.findByOrganizationIdAndPublicId(organizationId, eventId)
                .orElseThrow(CustomerEventServiceImpl::eventNotFound);
    }

    private CustomerEvent findEventForUpdate(UUID organizationId, UUID eventId) {
        if (organizationId == null || eventId == null) {
            throw eventNotFound();
        }
        return customerEventRepository.findByOrganizationIdAndPublicIdForUpdate(organizationId, eventId)
                .orElseThrow(CustomerEventServiceImpl::eventNotFound);
    }

    private User findCustomer(UUID organizationId, UUID customerId) {
        return userRepository.findByOrganizationIdAndRoleAndPublicId(
                        organizationId, UserRole.CUSTOMER, customerId)
                .orElseThrow(() -> new CustomException(ExceptionStatus.CUSTOMER_NOT_FOUND));
    }

    private CustomerEventStatus parseStatus(String value) {
        try {
            return CustomerEventStatus.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalidEvent();
        }
    }

    public static OffsetDateTime parseRequiredTime(String value) {
        if (value == null) {
            throw invalidEvent();
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException exception) {
            throw invalidEvent();
        }
    }

    private void requireOrganizationId(UUID organizationId) {
        if (organizationId == null) {
            throw new CustomException(ExceptionStatus.ACCESS_DENIED);
        }
    }

    private void requireLimit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw invalidEvent();
        }
    }

    private static CustomException invalidEvent() {
        return new CustomException(ExceptionStatus.INVALID_CUSTOMER_EVENT);
    }

    private static CustomException eventNotFound() {
        return new CustomException(ExceptionStatus.CUSTOMER_EVENT_NOT_FOUND);
    }
}
