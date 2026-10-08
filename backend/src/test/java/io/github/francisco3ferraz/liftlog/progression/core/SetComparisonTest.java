package io.github.francisco3ferraz.liftlog.progression.core;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.francisco3ferraz.liftlog.progression.core.LoggedSet.Kind;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SetComparisonTest {

    private static final int REP_MIN = 6;

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void comparesTodayWithBaseline(String description, LoggedSet today, LoggedSet baseline, SetComparison expected) {
        assertThat(SetComparison.compare(today, baseline, REP_MIN)).isEqualTo(expected);
    }

    static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of("higher weight at repMin is up", set("82.5", 6), set("80", 8), SetComparison.UP),
                Arguments.of("higher weight above repMin is up", set("82.5", 9), set("80", 8), SetComparison.UP),
                Arguments.of("equal weight with more reps is up", set("80", 9), set("80", 8), SetComparison.UP),
                Arguments.of(
                        "equal weight with more reps below repMin is up", set("80", 4), set("80", 3), SetComparison.UP),
                Arguments.of("equal weight with equal reps is equal", set("80", 8), set("80", 8), SetComparison.EQUAL),
                Arguments.of("weights equal regardless of scale", set("80.00", 8), set("80", 8), SetComparison.EQUAL),
                Arguments.of("lower weight is down", set("77.5", 12), set("80", 8), SetComparison.DOWN),
                Arguments.of("equal weight with fewer reps is down", set("80", 7), set("80", 8), SetComparison.DOWN),
                Arguments.of("higher weight below repMin is down", set("82.5", 5), set("80", 8), SetComparison.DOWN),
                Arguments.of("failed rep is not counted", failed("80", 8), set("80", 8), SetComparison.EQUAL),
                Arguments.of(
                        "failed rep does not lift a higher weight to repMin",
                        failed("82.5", 5),
                        set("80", 8),
                        SetComparison.DOWN),
                Arguments.of(
                        "lower RIR with equal weight and reps is equal",
                        withRir("80", 8, 0),
                        withRir("80", 8, 3),
                        SetComparison.EQUAL),
                Arguments.of(
                        "higher RIR with fewer reps is still down",
                        withRir("80", 7, 4),
                        withRir("80", 8, 0),
                        SetComparison.DOWN));
    }

    private static LoggedSet set(String weightKg, int completedReps) {
        return new LoggedSet(Kind.WORKING, new BigDecimal(weightKg), completedReps, false, Optional.empty());
    }

    private static LoggedSet failed(String weightKg, int completedReps) {
        return new LoggedSet(Kind.WORKING, new BigDecimal(weightKg), completedReps, true, Optional.empty());
    }

    private static LoggedSet withRir(String weightKg, int completedReps, int rir) {
        return new LoggedSet(Kind.WORKING, new BigDecimal(weightKg), completedReps, false, Optional.of(rir));
    }
}
