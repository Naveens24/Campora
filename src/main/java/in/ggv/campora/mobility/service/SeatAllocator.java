package in.ggv.campora.mobility.service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Pure seat-allocation policy, kept free of Spring/JPA so it is trivially
 * unit-testable.
 *
 * <p><b>Policy — FIFO first-fit.</b> Walk requests oldest-first; take each one
 * whose party still fits in the remaining seats. A group request (e.g. 2
 * friends) is never split across vehicles. Fairness: nobody is skipped
 * unless their whole party genuinely cannot fit, and a skipped group keeps
 * its place at the head of the queue for the next driver.</p>
 */
public final class SeatAllocator {

    private SeatAllocator() {
    }

    public static <T> List<T> allocate(List<T> oldestFirst, int capacity, ToIntFunction<T> seatsOf) {
        List<T> selected = new ArrayList<>();
        int remaining = capacity;
        for (T candidate : oldestFirst) {
            if (remaining == 0) {
                break;
            }
            int seats = seatsOf.applyAsInt(candidate);
            if (seats > 0 && seats <= remaining) {
                selected.add(candidate);
                remaining -= seats;
            }
        }
        return selected;
    }
}
