package carelog.carelog.customerevent.domain;

import carelog.carelog.user.domain.User;
import carelog.carelog.user.domain.UserRole;
import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerEventTest {

    private static final OffsetDateTime SCHEDULED_AT = OffsetDateTime.parse("2026-08-20T10:00:00+09:00");
    private static final OffsetDateTime OCCURRED_AT = OffsetDateTime.parse("2026-08-20T10:30:00+09:00");

    @Test
    @DisplayName("PLANNED 생성은 scheduledAt만 보존하고 overdue 상태를 저장하지 않는다")
    void createPlanned_preservesCanonicalTimeContract() {
        CustomerEvent event = CustomerEvent.createPlanned(customer(), SCHEDULED_AT, "상담", "메모");

        assertThat(event.getStatus()).isEqualTo(CustomerEventStatus.PLANNED);
        assertThat(event.getScheduledAt()).isEqualTo(SCHEDULED_AT);
        assertThat(event.getOccurredAt()).isNull();
    }

    @Test
    @DisplayName("즉시 OCCURRED 생성은 occurredAt만 필수로 가진다")
    void createOccurred_preservesCanonicalTimeContract() {
        CustomerEvent event = CustomerEvent.createOccurred(customer(), OCCURRED_AT, null, null);

        assertThat(event.getStatus()).isEqualTo(CustomerEventStatus.OCCURRED);
        assertThat(event.getScheduledAt()).isNull();
        assertThat(event.getOccurredAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    @DisplayName("PLANNED를 OCCURRED로 전이하면 원래 scheduledAt과 실제 occurredAt을 분리해 보존한다")
    void occur_preservesScheduleAndRecordsActualOccurrence() {
        CustomerEvent event = CustomerEvent.createPlanned(customer(), SCHEDULED_AT, null, null);

        event.occur(OCCURRED_AT);

        assertThat(event.getStatus()).isEqualTo(CustomerEventStatus.OCCURRED);
        assertThat(event.getScheduledAt()).isEqualTo(SCHEDULED_AT);
        assertThat(event.getOccurredAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    @DisplayName("종결 상태에서는 추가 lifecycle 전이를 거부한다")
    void terminalStatus_rejectsFurtherTransition() {
        CustomerEvent event = CustomerEvent.createPlanned(customer(), SCHEDULED_AT, null, null);
        event.cancel();

        assertThatThrownBy(() -> event.occur(OCCURRED_AT))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getExceptionStatus())
                                .isEqualTo(ExceptionStatus.INVALID_CUSTOMER_EVENT_TRANSITION));
    }

    private User customer() {
        User customer = User.builder().name("고객").role(UserRole.CUSTOMER).build();
        customer.assignOrganization(UUID.randomUUID());
        return customer;
    }
}
