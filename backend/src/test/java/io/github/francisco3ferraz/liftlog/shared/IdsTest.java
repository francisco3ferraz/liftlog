package io.github.francisco3ferraz.liftlog.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.UUID;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class IdsTest {

    @Nested
    class NewV7 {

        @Test
        void generatesVersion7WithTheRfc9562Variant() {
            var id = Ids.newV7();

            assertThat(id.version()).isEqualTo(7);
            assertThat(id.variant()).isEqualTo(2);
        }

        @Test
        void embedsTheClockTimeInTheFirst48Bits() {
            var now = Instant.parse("2026-10-09T12:34:56.789Z");
            var clock = Clock.fixed(now, ZoneOffset.UTC);

            var id = Ids.newV7(clock, RandomGenerator.getDefault());

            assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(now.toEpochMilli());
        }

        @Test
        void laterTimesSortAfterEarlierOnes() {
            var earlier = Clock.fixed(Instant.parse("2026-10-09T12:00:00Z"), ZoneOffset.UTC);
            var later = Clock.fixed(Instant.parse("2026-10-09T12:00:00.001Z"), ZoneOffset.UTC);
            var random = RandomGenerator.getDefault();

            var first = Ids.newV7(earlier, random);
            var second = Ids.newV7(later, random);

            assertThat(second.toString()).isGreaterThan(first.toString());
        }

        @Test
        void idsGeneratedInTheSameMillisecondAreDistinct() {
            var clock = Clock.fixed(Instant.parse("2026-10-09T12:00:00Z"), ZoneOffset.UTC);
            var random = RandomGenerator.getDefault();

            var ids = new HashSet<UUID>();
            IntStream.range(0, 10_000).forEach(i -> ids.add(Ids.newV7(clock, random)));

            assertThat(ids).hasSize(10_000);
        }
    }

    @Nested
    class RequireV7 {

        @Test
        void returnsAVersion7Id() {
            var id = UUID.fromString("0199c8a4-3f2e-7b1a-9c4d-5e6f7a8b9c0d");

            assertThat(Ids.requireV7(id)).isEqualTo(id);
        }

        @Test
        void rejectsAVersion4IdAsABadRequestWithTheInvalidIdCode() {
            var id = UUID.fromString("9b2f3c1e-4d5a-4e6f-8a7b-1c2d3e4f5a6b");

            assertThatExceptionOfType(InvalidIdException.class)
                    .isThrownBy(() -> Ids.requireV7(id))
                    .satisfies(e -> {
                        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(e.getBody().getProperties()).containsEntry("code", "invalid-id");
                        assertThat(e.getBody().getDetail()).contains(id.toString());
                    });
        }

        @Test
        void rejectsTheNilId() {
            assertThatExceptionOfType(InvalidIdException.class).isThrownBy(() -> Ids.requireV7(new UUID(0, 0)));
        }

        @Test
        void rejectsAVersion7BitPatternWithANonRfcVariant() {
            var id = UUID.fromString("0199c8a4-3f2e-7b1a-cc4d-5e6f7a8b9c0d");

            assertThatExceptionOfType(InvalidIdException.class).isThrownBy(() -> Ids.requireV7(id));
        }
    }
}
