package carelog.carelog.common.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SwaggerConfig {

    @Bean
    fun openAPI(): OpenAPI {
        val securitySchemeName = "bearerAuth"

        return OpenAPI()
            .info(Info()
                .version("v1.0.0")
                .title("Carelog API")
                .description("Carelog 서비스의 API 명세서입니다."))
            .servers(listOf(
                Server().url("http://localhost:8080").description("로컬 개발 서버"),
                Server().url("https://dev-api.carelog.com").description("개발 서버")
            ))
            .addSecurityItem(SecurityRequirement().addList(securitySchemeName))
            .components(Components()
                .addSecuritySchemes(securitySchemeName, SecurityScheme()
                    .name(securitySchemeName)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT 토큰을 입력해주세요. (Bearer 접두사 제외)")
                )
            )
    }
}
