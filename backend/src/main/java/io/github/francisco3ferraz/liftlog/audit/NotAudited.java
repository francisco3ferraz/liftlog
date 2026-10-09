package io.github.francisco3ferraz.liftlog.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Keeps a field of an {@link Audited} entity, such as a secret hash, out of the audited before and after state. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface NotAudited {}
