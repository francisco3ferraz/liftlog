package io.github.francisco3ferraz.liftlog.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

class CursorTest {

    private static final UUID ID = UUID.fromString("0199c8a4-3f2e-7b1a-9c4d-5e6f7a8b9c0d");

    @Nested
    class EncodeAndDecode {

        @Test
        void decodingAnEncodedCursorGivesItBack() {
            var cursor = new Cursor("2026-10-09T12:00:00Z", ID);

            assertThat(Cursor.decode(cursor.encode())).isEqualTo(cursor);
        }

        @Test
        void aSortKeyWithSeparatorsAndNonAsciiTextSurvivesTheRoundTrip() {
            var cursor = new Cursor("Supino: inclinado — 30°", ID);

            assertThat(Cursor.decode(cursor.encode())).isEqualTo(cursor);
        }

        @Test
        void encodesToUnpaddedBase64Url() {
            var encoded = new Cursor("Squat?", ID).encode();

            assertThat(encoded).matches("[A-Za-z0-9_-]+");
        }
    }

    @Nested
    class InvalidCursor {

        @ParameterizedTest
        @ValueSource(strings = {"", "not base64!", "Zm9v", "MDE5OWM4YTQtM2YyZS03YjFhLTljNGQtNWU2ZjdhOGI5YzBk"})
        void isABadRequestWithTheInvalidCursorCode(String cursor) {
            assertThatExceptionOfType(ErrorResponseException.class)
                    .isThrownBy(() -> Cursor.decode(cursor))
                    .satisfies(e -> {
                        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(e.getBody().getProperties()).containsEntry("code", "invalid-cursor");
                    });
        }

        @Test
        void aTamperedIdIsRejected() {
            var tampered = base64Url("1-2-3-4-5:Squat");

            assertThatExceptionOfType(ErrorResponseException.class).isThrownBy(() -> Cursor.decode(tampered));
        }

        @Test
        void invalidUtf8IsRejected() {
            var payload = (ID + ":").getBytes(StandardCharsets.UTF_8);
            var withBadByte = new byte[payload.length + 1];
            System.arraycopy(payload, 0, withBadByte, 0, payload.length);
            withBadByte[payload.length] = (byte) 0xFF;

            var tampered = Base64.getUrlEncoder().withoutPadding().encodeToString(withBadByte);

            assertThatExceptionOfType(ErrorResponseException.class).isThrownBy(() -> Cursor.decode(tampered));
        }

        @Test
        void anOverlongCursorIsRejectedBeforeDecoding() {
            var overlong = new Cursor("x".repeat(1000), ID).encode();

            assertThatExceptionOfType(ErrorResponseException.class).isThrownBy(() -> Cursor.decode(overlong));
        }

        private static String base64Url(String payload) {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Nested
    class Page {

        record Row(String name, UUID id) {}

        private static final List<Row> ROWS = List.of(
                new Row("Bench", UUID.fromString("0199c8a4-3f2e-7b1a-9c4d-000000000001")),
                new Row("Deadlift", UUID.fromString("0199c8a4-3f2e-7b1a-9c4d-000000000002")),
                new Row("Squat", UUID.fromString("0199c8a4-3f2e-7b1a-9c4d-000000000003")));

        @Test
        void anExtraFetchedRowIsDroppedAndTheLastKeptRowBecomesTheNextCursor() {
            var page = CursorPage.of(ROWS, 2, row -> new Cursor(row.name(), row.id()));

            assertThat(page.items()).containsExactly(ROWS.get(0), ROWS.get(1));
            assertThat(page.nextCursor())
                    .isEqualTo(new Cursor("Deadlift", ROWS.get(1).id()).encode());
        }

        @Test
        void theLastPageHasNoNextCursor() {
            var page = CursorPage.of(ROWS, 3, row -> new Cursor(row.name(), row.id()));

            assertThat(page.items()).isEqualTo(ROWS);
            assertThat(page.nextCursor()).isNull();
        }

        @Test
        void anEmptyPageHasNoNextCursor() {
            var page = CursorPage.<Row>of(List.of(), 20, row -> new Cursor(row.name(), row.id()));

            assertThat(page.items()).isEmpty();
            assertThat(page.nextCursor()).isNull();
        }

        @Test
        void limitDefaultsTo20() {
            assertThat(CursorPage.limitOrDefault(null)).isEqualTo(20);
            assertThat(CursorPage.limitOrDefault(7)).isEqualTo(7);
        }

        @Test
        void maximumLimitIs100() {
            assertThat(CursorPage.MAX_LIMIT).isEqualTo(100);
        }
    }
}
