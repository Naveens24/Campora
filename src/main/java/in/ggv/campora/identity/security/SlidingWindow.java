package in.ggv.campora.identity.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

/** Counts events per key over a recent period. In memory, which suits a single server. */
final class SlidingWindow {

    private final Map<String, Deque<Instant>> events = new HashMap<>();
    private final Duration window;
    private final Clock clock;

    SlidingWindow(Duration window, Clock clock) {
        this.window = window;
        this.clock = clock;
    }

    synchronized int count(String key) {
        Deque<Instant> recent = events.get(key);
        return recent == null ? 0 : trim(recent).size();
    }

    synchronized void add(String key) {
        trim(events.computeIfAbsent(key, k -> new ArrayDeque<>())).addLast(clock.instant());
    }

    synchronized void purge() {
        events.values().removeIf(recent -> trim(recent).isEmpty());
    }

    private Deque<Instant> trim(Deque<Instant> recent) {
        Instant cutoff = clock.instant().minus(window);
        while (!recent.isEmpty() && recent.peekFirst().isBefore(cutoff)) {
            recent.removeFirst();
        }
        return recent;
    }

    /**
     * The network a request came from, or empty when only a proxy or private address is
     * known: everyone would share it, so a limit on it would punish the whole campus.
     */
    static Optional<String> network(HttpServletRequest request) {
        try {
            InetAddress address = InetAddress.getByName(request.getRemoteAddr()); // an IP literal: no lookup
            if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
                return Optional.empty();
            }
            byte[] bytes = address.getAddress();
            // One IPv6 customer holds a whole /64.
            return Optional.of(HexFormat.of().formatHex(bytes, 0, bytes.length == 16 ? 8 : bytes.length));
        } catch (UnknownHostException e) {
            return Optional.empty();
        }
    }
}
