package io.github.francisco3ferraz.liftlog.shared;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The single source of the current time. Inject {@link Clock}; tests replace it with a fixed one. */
@Configuration(proxyBeanMethods = false)
class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
