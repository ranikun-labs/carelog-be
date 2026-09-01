package carelog.carelog.user.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(
        controllers = IdentityClaimsController.class,
        properties = "carelog.internal.identity-claims.enabled=false"
)
@AutoConfigureMockMvc(addFilters = false)
@Import(IdentityClaimsInternalApiConfiguration.class)
class IdentityClaimsDisabledConfigurationTest {

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("disabled mode에서는 private claims endpoint mapping이 등록되지 않는다")
    void disabledMode_doesNotRegisterEndpoint() {
        assertThat(handlerMapping.getHandlerMethods().keySet())
                .noneMatch(mapping -> mapping.getPatternValues().contains(
                        "/internal/v1/identity/accounts/{accountId}/claims"));
    }
}
