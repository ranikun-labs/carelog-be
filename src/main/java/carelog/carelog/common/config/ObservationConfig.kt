package carelog.carelog.common.config

import io.micrometer.observation.ObservationRegistry
import io.micrometer.observation.aop.ObservedAspect
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ObservationConfig {

    @Bean
    fun observedAspect(registry: ObservationRegistry): ObservedAspect =
        ObservedAspect(registry)
}