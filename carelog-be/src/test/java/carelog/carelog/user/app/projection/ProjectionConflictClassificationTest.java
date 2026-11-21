package carelog.carelog.user.app.projection;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectionConflictClassificationTest {
    @Test
    void onlyKnownUniqueConstraintsAreTerminalConflicts() {
        assertThat(classify("23505", "users_email_key")).isTrue();
        assertThat(classify("23505", "users_user_id_key")).isTrue();
        assertThat(classify("23505", "users_external_account_id_key")).isTrue();
        assertThat(classify("23505", "users_public_id_key")).isFalse();
        assertThat(classify("23503", "users_email_key")).isFalse();
        assertThat(classify("23505", null)).isFalse();
        assertThat(ExternalManagerProjectionService.isKnownDuplicate(new DataIntegrityViolationException("unknown"))).isFalse();
    }

    private boolean classify(String state, String constraint) {
        var violation = new ConstraintViolationException("database error", new SQLException("failure", state), constraint);
        return ExternalManagerProjectionService.isKnownDuplicate(new DataIntegrityViolationException("write failed", violation));
    }
}
