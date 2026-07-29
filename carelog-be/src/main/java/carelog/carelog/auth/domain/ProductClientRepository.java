package carelog.carelog.auth.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductClientRepository extends JpaRepository<ProductClient, Long> {

    Optional<ProductClient> findByClientId(String clientId);
}
