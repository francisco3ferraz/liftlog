package io.github.francisco3ferraz.liftlog.shared.web;

import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * One page of a cursor-paginated list. {@code nextCursor} is absent on the last page.
 *
 * @param items the page's items, in the list's order
 * @param nextCursor the opaque cursor for the following page, or {@code null} if there is none
 */
public record CursorPage<T>(List<T> items, @Nullable String nextCursor) {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    public CursorPage {
        items = List.copyOf(items);
    }

    /** The requested page size, or {@link #DEFAULT_LIMIT} when none was given. Range checks belong to the caller. */
    public static int limitOrDefault(@Nullable Integer limit) {
        return limit == null ? DEFAULT_LIMIT : limit;
    }

    /**
     * Builds a page from up to {@code limit + 1} rows fetched after the requested cursor. The extra row only signals
     * that another page exists; it is dropped, and the cursor points at the last row kept.
     */
    public static <T> CursorPage<T> of(List<T> fetched, int limit, Function<T, Cursor> cursorOf) {
        if (fetched.size() <= limit) {
            return new CursorPage<>(fetched, null);
        }
        var items = fetched.subList(0, limit);
        return new CursorPage<>(items, cursorOf.apply(items.getLast()).encode());
    }
}
