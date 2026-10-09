package io.github.francisco3ferraz.liftlog.audit;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/** Who made a change: a signed-in user, a named background task, or an anonymous request. Never absent. */
public sealed interface AuditActor {

    static AuditActor user(UUID id) {
        return new UserActor(id);
    }

    /** {@code name} is lowercase kebab-case, e.g. {@code auto-finish}. */
    static AuditActor system(String name) {
        return new SystemActor(name);
    }

    static AuditActor anonymous() {
        return AnonymousActor.INSTANCE;
    }

    /** The stored form: {@code user:<uuid>}, {@code system:<name>} or {@code anonymous}. */
    String value();

    /** The id of a user actor. */
    default Optional<UUID> userId() {
        return Optional.empty();
    }

    record UserActor(UUID id) implements AuditActor {

        @Override
        public String value() {
            return "user:" + id;
        }

        @Override
        public Optional<UUID> userId() {
            return Optional.of(id);
        }
    }

    record SystemActor(String name) implements AuditActor {

        private static final Pattern NAME = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

        public SystemActor {
            if (!NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("System actor name must be lowercase kebab-case: " + name);
            }
        }

        @Override
        public String value() {
            return "system:" + name;
        }
    }

    enum AnonymousActor implements AuditActor {
        INSTANCE;

        @Override
        public String value() {
            return "anonymous";
        }
    }
}
