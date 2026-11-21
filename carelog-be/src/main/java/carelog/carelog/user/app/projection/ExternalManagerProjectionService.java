package carelog.carelog.user.app.projection;

import carelog.carelog.user.domain.User;
import carelog.carelog.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import java.util.UUID;

/** 신뢰된 호출자가 검증한 외부 계정에 대한 내부 전용 투영. 공개 엔드포인트는 없다. */
@Service
@RequiredArgsConstructor
public class ExternalManagerProjectionService {
    private final UserRepository users;
    private final JdbcTemplate jdbc;

    @Transactional
    public Projection project(UUID externalAccountId, String userId, String email, String name) {
        Objects.requireNonNull(externalAccountId, "externalAccountId");
        if (userId == null || userId.isBlank() || email == null || email.isBlank() || name == null || name.isBlank()) {
            throw new IllegalArgumentException("Projection fields are required");
        }
        // 동일 외부 계정의 병렬 최초 요청을 트랜잭션 종료까지 직렬화한다.
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> null, externalAccountId.toString());
        var existing = users.findByExternalAccountId(externalAccountId);
        if (existing.isPresent()) {
            var receipts = jdbc.query("SELECT initial_user_id, initial_email, initial_name FROM external_manager_projection_receipts WHERE external_account_id = ?",
                    (rs, row) -> new Receipt(rs.getString(1), rs.getString(2), rs.getString(3)), externalAccountId);
            if (receipts.size() != 1 || !receipts.get(0).equals(new Receipt(userId, email, name))) {
                throw new ProjectionConflictException();
            }
            return toProjection(existing.get());
        }
        if (users.externalAccountExistsIncludingDeleted(externalAccountId)
                || users.localAccountExistsIncludingDeleted(externalAccountId)
                || users.existsByUserId(userId) || users.existsByEmail(email)) {
            throw new ProjectionConflictException();
        }
        try {
            // 기존 FK는 null로 유지하고 외부 계정과 별도 연결한다.
            User user = User.externalManager(externalAccountId, userId, email, name, UUID.randomUUID());
            User saved = users.saveAndFlush(user);
            jdbc.update("INSERT INTO external_manager_projection_receipts (external_account_id, initial_user_id, initial_email, initial_name) VALUES (?, ?, ?, ?)",
                    externalAccountId, userId, email, name);
            return toProjection(saved);
        } catch (DataIntegrityViolationException conflict) {
            // 알려진 유일성 경합만 최종 충돌로 분류한다. 다른 무결성 오류는 재시도 가능하게 전파한다.
            if (isKnownDuplicate(conflict)) throw new ProjectionConflictException();
            throw conflict;
        }
    }

    static boolean isKnownDuplicate(Throwable error) {
        Set<String> known = Set.of("users_user_id_key", "users_email_key", "users_external_account_id_key");
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof ConstraintViolationException violation
                    && "23505".equals(violation.getSQLState())
                    && violation.getConstraintName() != null
                    && known.contains(violation.getConstraintName())) return true;
        }
        return false;
    }

    private Projection toProjection(User user) {
        return new Projection(user.getExternalAccountId(), user.getPublicId(), user.getOrganizationId());
    }

    private record Receipt(String userId, String email, String name) {}

    public record Projection(UUID externalAccountId, UUID publicId, UUID organizationId) {}

    public static class ProjectionConflictException extends RuntimeException {
        public ProjectionConflictException() { super("Projection conflict"); }
    }
}
