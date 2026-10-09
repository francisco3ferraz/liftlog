package io.github.francisco3ferraz.liftlog.shared;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Runs every module's {@code @Scheduled} jobs. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfig {}
