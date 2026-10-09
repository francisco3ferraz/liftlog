package io.github.francisco3ferraz.liftlog.audit;

import io.github.francisco3ferraz.liftlog.shared.SoftDeletableEntity;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.persister.entity.EntityPersister;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Records every insert, update and delete of an {@link Audited} entity through {@link AuditRecorder}, while Hibernate
 * flushes, so the record shares the change's transaction. The before and after state are the entity's persistent
 * properties as JSON, without its {@link NotAudited} fields. Moving a soft-deletable entity to the trash is a
 * {@link AuditAction#DELETE} and taking it out again a {@link AuditAction#RESTORE}.
 */
@Component
class AuditingEventListener implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {

    private static final String DELETED_AT = "deletedAt";

    private static final ClassValue<Set<String>> NOT_AUDITED = new ClassValue<>() {
        @Override
        protected Set<String> computeValue(Class<?> type) {
            var names = Arrays.stream(type.getDeclaredFields())
                    .filter(field -> field.isAnnotationPresent(NotAudited.class))
                    .map(Field::getName)
                    .collect(Collectors.toSet());
            var superclass = type.getSuperclass();
            if (superclass != null) {
                names.addAll(get(superclass));
            }
            return Set.copyOf(names);
        }
    };

    private final AuditRecorder recorder;
    private final JsonMapper json;

    AuditingEventListener(AuditRecorder recorder, JsonMapper json) {
        this.recorder = recorder;
        this.json = json;
    }

    @Override
    public void onPostInsert(PostInsertEvent event) {
        var persister = event.getPersister();
        if (isAudited(persister)) {
            record(persister, event.getId(), AuditAction.CREATE, null, state(persister, event.getState()));
        }
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        var persister = event.getPersister();
        if (!isAudited(persister)) {
            return;
        }
        var oldState = event.getOldState();
        var before = oldState == null ? null : state(persister, oldState);
        var after = state(persister, event.getState());
        record(persister, event.getId(), updateAction(event.getEntity(), before, after), before, after);
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        var persister = event.getPersister();
        if (isAudited(persister)) {
            record(persister, event.getId(), AuditAction.DELETE, state(persister, event.getDeletedState()), null);
        }
    }

    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return false;
    }

    private static boolean isAudited(EntityPersister persister) {
        return persister.getMappedClass().isAnnotationPresent(Audited.class);
    }

    private static AuditAction updateAction(
            Object entity, @Nullable Map<String, @Nullable Object> before, Map<String, @Nullable Object> after) {
        if (entity instanceof SoftDeletableEntity && before != null) {
            var wasDeleted = before.get(DELETED_AT) != null;
            var isDeleted = after.get(DELETED_AT) != null;
            if (!wasDeleted && isDeleted) {
                return AuditAction.DELETE;
            }
            if (wasDeleted && !isDeleted) {
                return AuditAction.RESTORE;
            }
        }
        return AuditAction.UPDATE;
    }

    private static Map<String, @Nullable Object> state(EntityPersister persister, @Nullable Object[] values) {
        var names = persister.getPropertyNames();
        var excluded = NOT_AUDITED.get(persister.getMappedClass());
        var state = new LinkedHashMap<String, @Nullable Object>();
        for (var i = 0; i < names.length; i++) {
            if (!excluded.contains(names[i])) {
                state.put(names[i], values[i]);
            }
        }
        return state;
    }

    private void record(
            EntityPersister persister,
            Object id,
            AuditAction action,
            @Nullable Map<String, @Nullable Object> before,
            @Nullable Map<String, @Nullable Object> after) {
        recorder.record(
                persister.getMappedTableDetails().getTableName(), (UUID) id, action, toJson(before), toJson(after));
    }

    private @Nullable String toJson(@Nullable Map<String, @Nullable Object> state) {
        return state == null ? null : json.writeValueAsString(state);
    }
}
