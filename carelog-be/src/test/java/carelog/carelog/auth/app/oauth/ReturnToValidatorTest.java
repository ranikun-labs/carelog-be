package carelog.carelog.auth.app.oauth;

import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReturnToValidatorTest {

    private final ReturnToValidator validator = new ReturnToValidator(List.of("https://app.example.com:8443"));

    @Test
    void 상대경로와_정확히_일치하는_origin만_허용한다() {
        assertThat(validator.validate("/journals/42")).isEqualTo("/journals/42");
        assertThat(validator.validate("/journals/42?tab=notes&sort=recent"))
                .isEqualTo("/journals/42?tab=notes&sort=recent");
        assertThat(validator.validate("https://app.example.com:8443/onboarding"))
                .isEqualTo("https://app.example.com:8443/onboarding");
    }

    @Test
    void protocol_relative_외부_origin_유사호스트와_javascript를_거부한다() {
        assertInvalid("//evil.example.com");
        assertInvalid("https://app.example.com/onboarding");
        assertInvalid("https://app.example.com:8443.evil.example.com/onboarding");
        assertInvalid("javascript:alert(1)");
        assertInvalid("https://sub.app.example.com:8443/onboarding");
        assertInvalid("https://app.example.com:9443/onboarding");
    }

    @Test
    void network_path와_backslash_및_encoded_separator_우회를_거부한다() {
        assertInvalid("/\\evil.example.com");
        assertInvalid("/%5cevil.example.com");
        assertInvalid("/%2fevil.example.com");
        assertInvalid("//evil.example.com");
    }

    @Test
    void 제어문자와_CRLF를_거부한다() {
        assertInvalid("/journals/42\r\nLocation: https://evil.example.com");
        assertInvalid("/journals/42\u0000");
    }

    private void assertInvalid(String returnTo) {
        assertThatThrownBy(() -> validator.validate(returnTo))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.INVALID_OAUTH_RETURN_TO);
    }
}
