package carelog.carelog.customerevent.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerEventRepository extends JpaRepository<CustomerEvent, Long> {

    Optional<CustomerEvent> findByOrganizationIdAndPublicId(UUID organizationId, UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select event from CustomerEvent event
            where event.organizationId = :organizationId
              and event.publicId = :publicId
            """)
    Optional<CustomerEvent> findByOrganizationIdAndPublicIdForUpdate(
            @Param("organizationId") UUID organizationId,
            @Param("publicId") UUID publicId);

    @Query("""
            select event from CustomerEvent event
            where event.organizationId = :organizationId
              and coalesce(event.occurredAt, event.scheduledAt) >= :from
              and coalesce(event.occurredAt, event.scheduledAt) < :to
            order by coalesce(event.occurredAt, event.scheduledAt), event.publicId
            """)
    List<CustomerEvent> findSchedule(
            @Param("organizationId") UUID organizationId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            Pageable pageable);

    @Query("""
            select event from CustomerEvent event
            where event.organizationId = :organizationId
              and event.customer.publicId = :customerPublicId
              and coalesce(event.occurredAt, event.scheduledAt) >= :from
              and coalesce(event.occurredAt, event.scheduledAt) < :to
            order by coalesce(event.occurredAt, event.scheduledAt), event.publicId
            """)
    List<CustomerEvent> findCustomerSchedule(
            @Param("organizationId") UUID organizationId,
            @Param("customerPublicId") UUID customerPublicId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            Pageable pageable);

    @Query("""
            select event from CustomerEvent event
            where event.organizationId = :organizationId
              and event.customer.publicId = :customerPublicId
            order by event.createdAt desc, event.publicId desc
            """)
    List<CustomerEvent> findCustomerHistory(
            @Param("organizationId") UUID organizationId,
            @Param("customerPublicId") UUID customerPublicId,
            Pageable pageable);
}
