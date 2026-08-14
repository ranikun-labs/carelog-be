package carelog.carelog.customerevent;

import carelog.carelog.CarelogApplication;
import carelog.carelog.PostgreSqlTestContainerConfiguration;
import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import carelog.carelog.customerevent.app.CustomerEventService;
import carelog.carelog.customerevent.domain.CustomerEventRepository;
import carelog.carelog.customerevent.domain.CustomerEventStatus;
import carelog.carelog.customerevent.web.dto.CustomerEventCreateRequest;
import carelog.carelog.user.domain.User;
import carelog.carelog.user.domain.UserRepository;
import carelog.carelog.user.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = CarelogApplication.class)
@ActiveProfiles("test")
@Import(PostgreSqlTestContainerConfiguration.class)
class CustomerEventConcurrencyIntegrationTest {

    @Autowired
    private CustomerEventService customerEventService;

    @Autowired
    private CustomerEventRepository customerEventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void pessimisticLock_allowsOnlyOneTerminalTransition() throws Exception {
        UUID organizationId = UUID.randomUUID();
        User customer = User.builder().name("동시성 고객").role(UserRole.CUSTOMER).build();
        customer.assignOrganization(organizationId);
        customer = userRepository.saveAndFlush(customer);

        UUID eventId = customerEventService.create(organizationId, new CustomerEventCreateRequest(
                customer.getPublicId(), "PLANNED", "2026-08-20T10:00:00+09:00",
                null, "상담", null)).id();

        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseTransactionA = new CountDownLatch(1);
        CountDownLatch transactionBStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> occur = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                var event = customerEventRepository
                        .findByOrganizationIdAndPublicIdForUpdate(organizationId, eventId)
                        .orElseThrow();
                event.occur(OffsetDateTime.parse("2026-08-20T10:30:00+09:00"));
                lockAcquired.countDown();
                await(releaseTransactionA);
            }));

            assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).isTrue();

            Future<ExceptionStatus> cancel = executor.submit(() -> {
                transactionBStarted.countDown();
                try {
                    customerEventService.cancel(organizationId, eventId);
                    return null;
                } catch (CustomException exception) {
                    return exception.getExceptionStatus();
                }
            });

            assertThat(transactionBStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(awaitPostgreSqlLockWait()).isTrue();
            assertThat(cancel.isDone()).isFalse();

            releaseTransactionA.countDown();

            occur.get(5, TimeUnit.SECONDS);
            assertThat(cancel.get(5, TimeUnit.SECONDS))
                    .isEqualTo(ExceptionStatus.INVALID_CUSTOMER_EVENT_TRANSITION);
            assertThat(customerEventRepository.findByOrganizationIdAndPublicId(organizationId, eventId))
                    .get()
                    .extracting(event -> event.getStatus())
                    .isEqualTo(CustomerEventStatus.OCCURRED);
        } finally {
            releaseTransactionA.countDown();
            executor.shutdownNow();
        }
    }

    private boolean awaitPostgreSqlLockWait() throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            Integer waiting = jdbcTemplate.queryForObject("""
                    select count(*)
                    from pg_stat_activity
                    where pid <> pg_backend_pid()
                      and wait_event_type = 'Lock'
                      and lower(query) like '%customer_events%'
                    """, Integer.class);
            if (waiting != null && waiting > 0) {
                return true;
            }
            Thread.sleep(25);
        }
        return false;
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
