package io.github.francisco3ferraz.liftlog.shared;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;
import java.util.random.RandomGenerator;

/** UUIDv7 generation and validation. Clients generate most ids, so the server mostly validates. */
public final class Ids {

    private static final Clock SYSTEM_CLOCK = Clock.systemUTC();
    private static final RandomGenerator RANDOM = new SecureRandom();

    private static final int VERSION_7 = 7;
    private static final int RFC_9562_VARIANT = 2;

    private Ids() {}

    /** A new time-ordered UUIDv7: 48 bits of epoch millis, then 74 random bits. */
    public static UUID newV7() {
        return newV7(SYSTEM_CLOCK, RANDOM);
    }

    static UUID newV7(Clock clock, RandomGenerator random) {
        long msb = (clock.millis() << 16) | (VERSION_7 << 12) | (random.nextLong() & 0x0FFFL);
        long lsb = (random.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
        return new UUID(msb, lsb);
    }

    /** Returns {@code id} if it is a UUIDv7, otherwise throws {@link InvalidIdException}. */
    public static UUID requireV7(UUID id) {
        if (id.variant() != RFC_9562_VARIANT || id.version() != VERSION_7) {
            throw new InvalidIdException(id);
        }
        return id;
    }
}
