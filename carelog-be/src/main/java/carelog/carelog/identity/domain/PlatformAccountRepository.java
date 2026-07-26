package carelog.carelog.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PlatformAccountRepository extends JpaRepository<PlatformAccount, UUID> {
}
