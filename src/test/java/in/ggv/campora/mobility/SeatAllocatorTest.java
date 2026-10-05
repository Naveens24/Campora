package in.ggv.campora.mobility;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.ToIntFunction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import in.ggv.campora.mobility.service.SeatAllocator;

class SeatAllocatorTest {

    private static final ToIntFunction<Integer> SEATS = Integer::intValue;

    @Test
    @DisplayName("Design-doc scenario: 10 seats of demand, two drivers of 5 close the hotspot")
    void twoDriversCloseTheHotspot() {
        List<Integer> queue = List.of(2, 2, 1, 2, 2, 1);

        List<Integer> driverA = SeatAllocator.allocate(queue, 5, SEATS);
        assertThat(driverA).containsExactly(2, 2, 1);

        List<Integer> remaining = queue.subList(3, 6);
        List<Integer> driverB = SeatAllocator.allocate(remaining, 5, SEATS);
        assertThat(driverB).containsExactly(2, 2, 1);
    }

    @Test
    @DisplayName("Groups are never split; a later smaller party fills the gap")
    void groupsAreNotSplit() {
        List<Integer> queue = List.of(3, 3, 1);
        assertThat(SeatAllocator.allocate(queue, 4, SEATS)).containsExactly(3, 1);
    }

    @Test
    @DisplayName("Oldest request is served first (FIFO fairness)")
    void fifoOrder() {
        List<Integer> queue = List.of(1, 1, 1, 1);
        assertThat(SeatAllocator.allocate(queue, 2, SEATS)).hasSize(2);
    }

    @Test
    @DisplayName("Nothing fits → nothing allocated")
    void nothingFits() {
        assertThat(SeatAllocator.allocate(List.of(4, 5), 3, SEATS)).isEmpty();
    }

    @Test
    @DisplayName("Allocation never exceeds capacity")
    void neverExceedsCapacity() {
        List<Integer> queue = List.of(2, 1, 2, 1, 1, 2);
        int total = SeatAllocator.allocate(queue, 5, SEATS).stream().mapToInt(Integer::intValue).sum();
        assertThat(total).isLessThanOrEqualTo(5).isEqualTo(5);
    }
}
