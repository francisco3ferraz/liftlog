package io.github.francisco3ferraz.liftlog.shared.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.accept.PathApiVersionResolver;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Reads the API version from the path segment after {@code /api}, so {@code /api/v1/sessions} is version 1, and
 * leaves every other path unversioned. Controllers map {@code /api/{version}/...}; the supported versions are set in
 * {@code spring.mvc.apiversion}. The path-segment property can't do this because it versions every path.
 */
@Configuration(proxyBeanMethods = false)
class ApiVersioningConfig implements WebMvcConfigurer {

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer.useVersionResolver(new PathApiVersionResolver(
                1, path -> path.pathWithinApplication().value().startsWith("/api/")));
    }
}
