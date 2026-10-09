package io.github.francisco3ferraz.liftlog.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an entity whose inserts, updates and deletes are recorded in the audit log automatically, in the same
 * transaction. Audited entities must only be written through the persistence context: bulk and native statements
 * bypass auditing.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Audited {}
