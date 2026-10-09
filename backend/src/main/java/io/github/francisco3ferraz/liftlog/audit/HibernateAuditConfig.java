package io.github.francisco3ferraz.liftlog.audit;

import jakarta.persistence.EntityManagerFactory;
import java.util.Objects;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.springframework.context.annotation.Configuration;

/** Registers {@link AuditingEventListener} with Hibernate, which does not discover Spring beans as listeners. */
@Configuration(proxyBeanMethods = false)
class HibernateAuditConfig {

    HibernateAuditConfig(EntityManagerFactory entityManagerFactory, AuditingEventListener listener) {
        var registry = Objects.requireNonNull(entityManagerFactory
                .unwrap(SessionFactoryImplementor.class)
                .getServiceRegistry()
                .getService(EventListenerRegistry.class));
        registry.appendListeners(EventType.POST_INSERT, listener);
        registry.appendListeners(EventType.POST_UPDATE, listener);
        registry.appendListeners(EventType.POST_DELETE, listener);
    }
}
