package carelog.carelog.user.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByPublicId(UUID publicId);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByUserId(String userId);

    boolean existsByUserId(String userId);
}
