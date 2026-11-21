package carelog.carelog.user.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByPublicId(UUID publicId);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByUserId(String userId);

    boolean existsByUserId(String userId);

    Optional<User> findByAccountId(UUID accountId);

    Optional<User> findByExternalAccountId(UUID externalAccountId);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM users WHERE external_account_id = :externalId)", nativeQuery = true)
    boolean externalAccountExistsIncludingDeleted(@Param("externalId") UUID externalId);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM users WHERE account_id = :accountId)", nativeQuery = true)
    boolean localAccountExistsIncludingDeleted(@Param("accountId") UUID accountId);

    // --- Step B: Customer 조회 ---
    List<User> findAllByRole(UserRole role);

    List<User> findAllByRoleAndNameContaining(UserRole role, String name);

    // Product Customer API는 Hibernate Tenant Filter와 별개로 모든 조회 조건에 조직 범위를 포함한다.
    List<User> findAllByOrganizationIdAndRole(UUID organizationId, UserRole role);

    List<User> findAllByOrganizationIdAndRoleAndNameContaining(
            UUID organizationId, UserRole role, String name);

    Optional<User> findByOrganizationIdAndRoleAndPublicId(
            UUID organizationId, UserRole role, UUID publicId);
}
