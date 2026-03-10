package carelog.carelog.common.config

import carelog.carelog.common.web.dto.response.ApiResponse
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import io.swagger.v3.oas.models.responses.ApiResponse as SwaggerApiResponse


@Component
class GlobalResponseCustomizer: OperationCustomizer {

    override fun customize(operation: Operation, handlerMethod: HandlerMethod): Operation {
        val response = operation.responses
        response.addApiResponse("400", createApiResponse("잘못된 요청", 400))
        response.addApiResponse("401", createApiResponse("인증 실패", 401))
        response.addApiResponse("403", createApiResponse("권한 없음", 403))
        response.addApiResponse("404", createApiResponse("리소스 없음", 404))
        response.addApiResponse("500", createApiResponse("서버 오류", 500))
        return operation

    }

    private fun createApiResponse(description: String, statusCode: Int): SwaggerApiResponse {
        val schema = ModelConverters.getInstance()
            .resolveAsResolvedSchema(AnnotatedType(ApiResponse::class.java)).schema

        return SwaggerApiResponse()
            .description(description)
            .content(Content().addMediaType("application/json",
                MediaType().schema(schema as Schema<*>)))
    }
}