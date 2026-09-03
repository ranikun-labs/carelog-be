package carelog.carelog.auth.app.oauth;

import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReturnToValidatorTest {

    private final ReturnToValidator validator = new ReturnToValidator(List.of("https://app.example.com:8443"));

    @ParameterizedTest
    @ValueSource(strings = {
            "/",
            "/app",
            "/customers",
            "/customers/42",
            "/customers/42?tab=notes&sort=recent"
    })
    void 안전한_ASCII_로컬_경로와_query는_원문을_보존한다(String returnTo) {
        assertThat(validator.validate(returnTo)).isEqualTo(returnTo);
    }

    @Test
    void 최대_길이_2048의_경로는_원문을_보존한다() {
        String returnTo = "/" + "a".repeat(2047);

        assertThat(validator.validate(returnTo)).isEqualTo(returnTo);
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {
            " ",
            "customers",
            "http://evil.example",
            "https://evil.example",
            "https://app.example.com:8443/onboarding",
            "//evil.example",
            "javascript:alert(1)",
            "data:text/html,evil",
            "/customers#details",
            "/\\evil.example",
            "/customers%",
            "/customers%41",
            "/customers/%2fadmin",
            "/customers/%2e%2e/admin",
            "/customers/./42",
            "/customers/../admin",
            "/patient list",
            "/?q=a b",
            "/customers\t42",
            "/customers\r\nLocation:https://evil.example",
            "/customers\u0000",
            "/고객"
    })
    void path_only_계약을_벗어난_입력은_거부한다(String returnTo) {
        assertInvalid(returnTo);
    }

    @Test
    void 최대_길이_2048을_초과하면_거부한다() {
        assertInvalid("/" + "a".repeat(2048));
    }

    private void assertInvalid(String returnTo) {
        assertThatThrownBy(() -> validator.validate(returnTo))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.INVALID_OAUTH_RETURN_TO);
    }
}
