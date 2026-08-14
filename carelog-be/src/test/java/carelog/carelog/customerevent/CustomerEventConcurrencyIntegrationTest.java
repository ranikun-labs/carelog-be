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
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

    @Test
    void pessimisticLock_allowsOnlyOneTerminalTransition() throws Exception {
        UUID organizationId = UUID.randomUUID();
        User customer = User.builder().name("동시성 고객").role(UserRole.CUSTOMER).build();
        customer.assignOrganization(organizationId);
        customer = userRepository.saveAndFlush(customer);

        UUID eventId = customerEventService.create(organizationId, new CustomerEventCreateRequest(
                customer.getPublicId(), "PLANNED", "2026-08-20T10:00:00+09:00",
                null, "상담", null)).id();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ExceptionStatus> occur = executor.submit(() -> runTransition(ready, start,
                    () -> customerEventService.occur(
                            organizationId, eventId, OffsetDateTime.parse("2026-08-20T10:30:00+09:00"))));
            Future<ExceptionStatus> cancel = executor.submit(() -> runTransition(ready, start,
                    () -> customerEventService.cancel(organizationId, eventId)));

            ready.await();
            start.countDown();

            List<ExceptionStatus> results = Arrays.asList(occur.get(), cancel.get());
            assertThat(results).containsExactlyInAnyOrder(null, ExceptionStatus.INVALID_CUSTOMER_EVENT_TRANSITION);
            assertThat(customerEventRepository.findByOrganizationIdAndPublicId(organizationId, eventId))
                    .get()
                    .extracting(event -> event.getStatus())
                    .isIn(CustomerEventStatus.OCCURRED, CustomerEventStatus.CANCELLED);
        } finally {
            executor.shutdownNow();
        }
    }

    private ExceptionStatus runTransition(
            CountDownLatch ready, CountDownLatch start, Runnable transition) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            transition.run();
            return null;
        } catch (CustomException exception) {
            return exception.getExceptionStatus();
        }
    }
}
