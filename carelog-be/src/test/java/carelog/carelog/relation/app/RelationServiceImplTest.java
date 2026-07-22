package carelog.carelog.relation.app;

import carelog.carelog.CarelogApplication;
import carelog.carelog.PostgreSqlTestContainerConfiguration;
import carelog.carelog.relation.app.*;
import carelog.carelog.user.domain.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.*;

@SpringBootTest(classes = CarelogApplication.class)
@ActiveProfiles("test")
@Import(PostgreSqlTestContainerConfiguration.class)
@Transactional
class RelationServiceImplTest {

    @Autowired
    private RelationService relationService;

    @Autowired
    private UserRepository userRepository;

    @DisplayName("두명의 사용자가 동시에 관계 생성 요청시, 하나의 요청만 성공해야함")
    @Test
    void createRelation_concurrency_test() throws InterruptedException {
        // given : 테스트용 사용자 미리 저장
        // User 생성자의 파라미터는 실제
    }

//    @DisplayName("두 명의 사용자가 동시에 관계 생성을 요청할 경우, 하나의 요청만 성공해야 한다.")
//    @Test
//    void createRelation_concurrency_test() throws InterruptedException {
//        // given: 테스트용 사용자 미리 저장
//        // User 생성자의 파라미터는 실제 User 엔티티에 맞게 수정이 필요할 수 있습니다.
//        User manager = userRepository.save(new User("manager1", null, null, null, null));
//        User customer = userRepository.save(new User("customer1", null, null, null, null));
//
//        int threadCount = 2;
//        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
//        CountDownLatch latch = new CountDownLatch(threadCount);
//        AtomicInteger successCount = new AtomicInteger();
//        AtomicInteger failureCount = new AtomicInteger();
//
//        // when: 2개의 스레드가 동시에 관계 생성을 요청
//        for (int i = 0; i < threadCount; i++) {
//            executorService.submit(() -> {
//                try {
//                    relationService.createRelation(manager.getId(), customer.getId());
//                    successCount.getAndIncrement();
//                } catch (Exception e) {
//                    // 예외가 발생하면 실패 카운트 증가
//                    // (예: DataIntegrityViolationException 또는 커스텀 예외)
//                    failureCount.getAndIncrement();
//                } finally {
//                    latch.countDown();
//                }
//            });
//        }
//
//        latch.await(); // 모든 스레드가 작업을 완료할 때까지 대기
//
//        // then: 하나의 요청만 성공하고, 다른 하나는 실패해야 함
//        assertThat(successCount.get()).isEqualTo(1);
//        assertThat(failureCount.get()).isEqualTo(1);
//    }

}
