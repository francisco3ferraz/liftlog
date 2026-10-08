package io.github.francisco3ferraz.liftlog.progression.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.github.francisco3ferraz.liftlog.progression.core.LoggedSet.Kind;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CoreValueTypesTest {

    @Nested
    class LoggedSetTest {

        @Test
        void acceptsAValidWorkingSet() {
            var set = new LoggedSet(Kind.WORKING, new BigDecimal("82.5"), 8, false, Optional.of(1));

            assertThat(set.isWorking()).isTrue();
            assertThat(set.weightKg()).isEqualByComparingTo("82.50");
            assertThat(set.completedReps()).isEqualTo(8);
            assertThat(set.rir()).contains(1);
        }

        @Test
        void warmupSetsAreNotWorking() {
            var set = new LoggedSet(Kind.WARMUP, new BigDecimal("40"), 10, false, Optional.empty());

            assertThat(set.isWorking()).isFalse();
        }

        @Test
        void acceptsZeroWeightAndZeroReps() {
            var set = new LoggedSet(Kind.WORKING, BigDecimal.ZERO, 0, true, Optional.empty());

            assertThat(set.weightKg()).isEqualByComparingTo("0");
            assertThat(set.completedReps()).isZero();
        }

        @ParameterizedTest
        @ValueSource(strings = {"0.25", "0.50", "1.75", "100.00", "2.500"})
        void acceptsWeightsInQuarterKiloSteps(String weight) {
            var set = new LoggedSet(Kind.WORKING, new BigDecimal(weight), 5, false, Optional.empty());

            assertThat(set.weightKg()).isEqualByComparingTo(weight);
        }

        @Test
        void rejectsNegativeWeight() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new LoggedSet(Kind.WORKING, new BigDecimal("-2.5"), 5, false, Optional.empty()))
                    .withMessageContaining("weightKg");
        }

        @ParameterizedTest
        @ValueSource(strings = {"0.1", "1.3", "80.01", "2.2"})
        void rejectsWeightsOffTheQuarterKiloGrid(String weight) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new LoggedSet(Kind.WORKING, new BigDecimal(weight), 5, false, Optional.empty()))
                    .withMessageContaining("weightKg");
        }

        @Test
        void rejectsNegativeReps() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new LoggedSet(Kind.WORKING, new BigDecimal("50"), -1, false, Optional.empty()))
                    .withMessageContaining("completedReps");
        }

        @Test
        void rejectsRirOnAFailedSet() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new LoggedSet(Kind.WORKING, new BigDecimal("50"), 5, true, Optional.of(0)))
                    .withMessageContaining("rir");
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 4})
        void acceptsRirAtTheBoundsOfTheScale(int rir) {
            var set = new LoggedSet(Kind.WORKING, new BigDecimal("50"), 5, false, Optional.of(rir));

            assertThat(set.rir()).contains(rir);
        }

        @ParameterizedTest
        @ValueSource(ints = {-1, 5})
        void rejectsRirOutsideTheScale(int rir) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new LoggedSet(Kind.WORKING, new BigDecimal("50"), 5, false, Optional.of(rir)))
                    .withMessageContaining("rir");
        }
    }

    @Nested
    class LineSettingsTest {

        @Test
        void acceptsValidSettings() {
            var line = new LineSettings(6, 10, new BigDecimal("2.5"), 1, 2, 3, false);

            assertThat(line.repMin()).isEqualTo(6);
            assertThat(line.repMax()).isEqualTo(10);
            assertThat(line.incrementKg()).isEqualByComparingTo("2.5");
            assertThat(line.prescribedSets()).isEqualTo(3);
            assertThat(line.rirGate()).isFalse();
        }

        @Test
        void acceptsASingleRepTargetAndEqualRirBounds() {
            var line = new LineSettings(1, 1, new BigDecimal("0.25"), 2, 2, 1, true);

            assertThat(line.repMin()).isEqualTo(line.repMax());
            assertThat(line.targetRirMin()).isEqualTo(line.targetRirMax());
        }

        @Test
        void rejectsRepMinBelowOne() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new LineSettings(0, 10, new BigDecimal("2.5"), 1, 2, 3, false))
                    .withMessageContaining("repMin");
        }

        @Test
        void rejectsRepMaxBelowRepMin() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new LineSettings(10, 8, new BigDecimal("2.5"), 1, 2, 3, false))
                    .withMessageContaining("repMax");
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "-2.5"})
        void rejectsNonPositiveIncrement(String increment) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new LineSettings(6, 10, new BigDecimal(increment), 1, 2, 3, false))
                    .withMessageContaining("incrementKg");
        }

        @Test
        void rejectsZeroPrescribedSets() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new LineSettings(6, 10, new BigDecimal("2.5"), 1, 2, 0, false))
                    .withMessageContaining("prescribedSets");
        }

        @Test
        void acceptsTargetRirAtTheBoundsOfTheScale() {
            var line = new LineSettings(6, 10, new BigDecimal("2.5"), 0, 4, 3, true);

            assertThat(line.targetRirMin()).isZero();
            assertThat(line.targetRirMax()).isEqualTo(4);
        }

        @ParameterizedTest
        @ValueSource(ints = {-1, 5})
        void rejectsTargetRirMinOutsideTheScale(int targetRirMin) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new LineSettings(6, 10, new BigDecimal("2.5"), targetRirMin, 4, 3, false))
                    .withMessageContaining("targetRirMin");
        }

        @ParameterizedTest
        @ValueSource(ints = {-1, 5})
        void rejectsTargetRirMaxOutsideTheScale(int targetRirMax) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new LineSettings(6, 10, new BigDecimal("2.5"), 0, targetRirMax, 3, false))
                    .withMessageContaining("targetRirMax");
        }

        @Test
        void rejectsTargetRirMaxBelowTargetRirMin() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new LineSettings(6, 10, new BigDecimal("2.5"), 3, 1, 3, false))
                    .withMessageContaining("targetRirMax");
        }
    }
}
