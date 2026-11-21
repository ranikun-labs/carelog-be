package carelog.carelog.user.app.projection;

import carelog.carelog.CarelogApplication;
import carelog.carelog.PostgreSqlTestContainerConfiguration;
import carelog.carelog.auth.app.port.CRMIdentityProjectionPort;
import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.identity.domain.PasswordCredentialRepository;
import carelog.carelog.user.app.UserService;
import carelog.carelog.auth.app.UserPrincipal;
import carelog.carelog.relation.app.RelationService;
import carelog.carelog.user.domain.User;
import carelog.carelog.user.domain.UserRole;
import carelog.carelog.user.domain.ManagerType;
import carelog.carelog.user.web.dto.ManagerCreateRequest;
import carelog.carelog.user.domain.UserRepository;
import carelog.carelog.user.web.dto.UserUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = CarelogApplication.class)
@ActiveProfiles("test")
@Import(PostgreSqlTestContainerConfiguration.class)
class ExternalManagerProjectionIntegrationTest {
    @Autowired ExternalManagerProjectionService service;
    @Autowired UserRepository users;
    @Autowired PasswordCredentialRepository credentials;
    @Autowired CRMIdentityProjectionPort claims;
    @Autowired UserService userService;
    @Autowired RelationService relationService;
    @Autowired TransactionTemplate transactions;

    @Test
    void replayUsesOriginalReceiptAndPreservesTenantWithoutLocalCredential() {
        UUID external = UUID.randomUUID();
        String userId = "u" + UUID.randomUUID();
        String email = userId + "@example.test";
        var first = service.project(external, userId, email, "Initial");
        var user = users.findByExternalAccountId(external).orElseThrow();
        assertThat(user.getAccountId()).isNull();
        assertThat(credentials.findByLoginId(userId)).isEmpty();
        assertThat(user.getPassword()).isNull();
        assertThat(claims.getIdentityClaims(external).organizationId()).isEqualTo(first.organizationId());
        transactions.executeWithoutResult(status -> users.findByExternalAccountId(external).orElseThrow().updateName("Changed"));
        assertThat(service.project(external, userId, email, "Initial")).isEqualTo(first);
        assertThatThrownBy(() -> service.project(external, userId, email, "Changed"))
                .isInstanceOf(ExternalManagerProjectionService.ProjectionConflictException.class);
        assertThatThrownBy(() -> userService.updateUser(first.publicId(), new UserUpdateRequest("new-password", null, null)))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> service.project(UUID.randomUUID(), userId, email, "Other"))
                .isInstanceOf(ExternalManagerProjectionService.ProjectionConflictException.class);
    }

    @Test
    void concurrentSameExternalIdReturnsSameProjection() throws Exception {
        UUID external = UUID.randomUUID();
        String userId = "u" + UUID.randomUUID();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> service.project(external, userId, userId + "@example.test", "Initial"));
            var second = executor.submit(() -> service.project(external, userId, userId + "@example.test", "Initial"));
            assertThat(first.get()).isEqualTo(second.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentDuplicateLoginHasOneWinnerAndNoLinkedLoser() throws Exception {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        String userId = "u" + UUID.randomUUID();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> attempt(firstId, userId));
            var second = executor.submit(() -> attempt(secondId, userId));
            assertThat(java.util.List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
            assertThat(users.findByExternalAccountId(firstId).isPresent() ^ users.findByExternalAccountId(secondId).isPresent()).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean attempt(UUID external, String userId) {
        try {
            service.project(external, userId, userId + "@example.test", "Initial");
            return true;
        } catch (ExternalManagerProjectionService.ProjectionConflictException conflict) {
            return false;
        }
    }

    @Test
    void externalManagerCreatesAndListsOnlyWithinItsTenant() {
        UUID external = UUID.randomUUID();
        String userId = "u" + UUID.randomUUID();
        var projected = service.project(external, userId, userId + "@example.test", "Initial");
        UUID customerId = createCustomer(projected.organizationId());
        UUID otherCustomerId = createCustomer(UUID.randomUUID());
        UserPrincipal principal = principal(external, projected.organizationId(), projected.publicId());
        var relation = relationService.createRelation(customerId, principal);
        assertThat(relationService.findAllRelationsByManager(principal)).contains(relation);
        assertThatThrownBy(() -> relationService.createRelation(otherCustomerId, principal))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> relationService.findAllRelationsByManager(
                principal(external, UUID.randomUUID(), projected.publicId())))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> relationService.findAllRelationsByManager(
                principal(external, projected.organizationId(), UUID.randomUUID())))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void legacyManagerStillCreatesAndListsWithinItsTenant() {
        String userId = "u" + UUID.randomUUID().toString().substring(0, 12);
        var created = userService.createManager(new ManagerCreateRequest(
                userId, userId + "@example.test", "password123", "Legacy", ManagerType.PHYSICAL_THERAPIST, null, null));
        var manager = users.findByPublicId(created.publicId()).orElseThrow();
        UUID customerId = createCustomer(manager.getOrganizationId());
        UserPrincipal principal = principal(manager.getAccountId(), manager.getOrganizationId(), manager.getPublicId());
        var relation = relationService.createRelation(customerId, principal);
        assertThat(relationService.findAllRelationsByManager(principal)).contains(relation);
    }

    private UUID createCustomer(UUID organizationId) {
        return transactions.execute(status -> {
            User customer = User.builder().name("Customer").role(UserRole.CUSTOMER).build();
            customer.assignOrganization(organizationId);
            return users.saveAndFlush(customer).getPublicId();
        });
    }

    private UserPrincipal principal(UUID accountId, UUID organizationId, UUID publicId) {
        return new UserPrincipal() {
            public UUID getAccountId() { return accountId; }
            public String getUserId() { return "synthetic"; }
            public UUID getOrganizationId() { return organizationId; }
            public String getRole() { return "MANAGER"; }
            public UUID getPublicId() { return publicId; }
        };
    }

    @Test
    void deletedProjectionCannotBeReusedOrFallBack() {
        UUID external = UUID.randomUUID();
        String userId = "u" + UUID.randomUUID();
        var result = service.project(external, userId, userId + "@example.test", "Initial");
        userService.deleteUser(result.publicId());
        assertThat(claims.findIdentityClaims(external)).isEmpty();
        assertThatThrownBy(() -> service.project(external, userId, userId + "@example.test", "Initial"))
                .isInstanceOf(ExternalManagerProjectionService.ProjectionConflictException.class);
    }
}
