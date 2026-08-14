package carelog.carelog.customerevent.domain;

import carelog.carelog.common.domain.TenantBaseEntity;
import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import carelog.carelog.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "customer_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerEvent extends TenantBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "public_id", unique = true, nullable = false, updatable = false)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private User customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CustomerEventStatus status;

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    @Column(name = "occurred_at")
    private OffsetDateTime occurredAt;

    @Column(name = "descriptor", length = 200)
    private String descriptor;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    private CustomerEvent(
            User customer,
            CustomerEventStatus status,
            OffsetDateTime scheduledAt,
            OffsetDateTime occurredAt,
            String descriptor,
            String note
    ) {
        this.publicId = UUID.randomUUID();
        this.customer = customer;
        this.status = status;
        this.scheduledAt = scheduledAt;
        this.occurredAt = occurredAt;
        this.descriptor = normalizeOptional(descriptor);
        this.note = normalizeOptional(note);
    }

    public static CustomerEvent createPlanned(
            User customer, OffsetDateTime scheduledAt, String descriptor, String note) {
        if (customer == null || scheduledAt == null) {
            throw invalidEvent();
        }
        return new CustomerEvent(
                customer, CustomerEventStatus.PLANNED, scheduledAt, null, descriptor, note);
    }

    public static CustomerEvent createOccurred(
            User customer, OffsetDateTime occurredAt, String descriptor, String note) {
        if (customer == null || occurredAt == null) {
            throw invalidEvent();
        }
        return new CustomerEvent(
                customer, CustomerEventStatus.OCCURRED, null, occurredAt, descriptor, note);
    }

    public void occur(OffsetDateTime occurredAt) {
        requirePlanned();
        if (occurredAt == null) {
            throw invalidEvent();
        }
        this.status = CustomerEventStatus.OCCURRED;
        this.occurredAt = occurredAt;
    }

    public void cancel() {
        requirePlanned();
        this.status = CustomerEventStatus.CANCELLED;
    }

    public void updateDescriptor(String descriptor) {
        this.descriptor = normalizeOptional(descriptor);
    }

    public void updateNote(String note) {
        this.note = normalizeOptional(note);
    }

    public void reschedule(OffsetDateTime scheduledAt) {
        requirePlanned();
        if (scheduledAt == null) {
            throw invalidEvent();
        }
        this.scheduledAt = scheduledAt;
    }

    public void updateOccurredAt(OffsetDateTime occurredAt) {
        if (status != CustomerEventStatus.OCCURRED) {
            throw invalidTransition();
        }
        if (occurredAt == null) {
            throw invalidEvent();
        }
        this.occurredAt = occurredAt;
    }

    private void requirePlanned() {
        if (status != CustomerEventStatus.PLANNED) {
            throw invalidTransition();
        }
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static CustomException invalidEvent() {
        return new CustomException(ExceptionStatus.INVALID_CUSTOMER_EVENT);
    }

    private static CustomException invalidTransition() {
        return new CustomException(ExceptionStatus.INVALID_CUSTOMER_EVENT_TRANSITION);
    }
}
