package carelog.gateway.ratelimit

import carelog.gateway.config.OAuthRateLimitConfig
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

class OAuthClientIpKeyResolver(
    oauthRateLimitConfig: OAuthRateLimitConfig
) : KeyResolver {

    private val remoteAddressResolver =
        XForwardedRemoteAddressResolver.maxTrustedIndex(oauthRateLimitConfig.trustedProxyHops)

    override fun resolve(exchange: ServerWebExchange): Mono<String> = Mono.defer {
        val address = remoteAddressResolver.resolve(exchange)
        Mono.justOrEmpty(address?.address?.hostAddress ?: address?.hostString)
            .filter { key -> !key.isNullOrBlank() }
    }
}
