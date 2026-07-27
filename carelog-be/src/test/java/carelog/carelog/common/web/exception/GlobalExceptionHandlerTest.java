package carelog.carelog.common.web.exception;

import carelog.carelog.auth.app.port.oauth.OAuthStateStoreUnavailableException;
import carelog.carelog.common.web.dto.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void OAuth_state_store_장애는_원인정보_없이_503_안전응답으로_매핑한다() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<ApiResponse<Object>> response = handler.handleOAuthStateStoreUnavailable(
                new OAuthStateStoreUnavailableException("redis://secret-host:6379 unavailable", new RuntimeException("secret"))
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).satisfies(body -> {
            assertThat(body.getStatus()).isEqualTo(503);
            assertThat(body.getMessage()).isEqualTo(ExceptionStatus.OAUTH_STATE_STORE_UNAVAILABLE.getMessage());
            assertThat(body.getMessage()).doesNotContain("secret-host", "redis://", "secret");
            assertThat(body.getData()).isNull();
        });
    }
}
