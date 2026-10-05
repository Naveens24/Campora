package in.ggv.campora.mobility.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Sweeps unserved requests past their TTL so hotspots reflect real, current demand. */
@Component
public class RequestExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(RequestExpiryJob.class);

    private final TravelRequestService requests;

    public RequestExpiryJob(TravelRequestService requests) {
        this.requests = requests;
    }

    @Scheduled(fixedDelayString = "${campora.mobility.expiry-sweep-ms:60000}", initialDelay = 30_000)
    public void sweep() {
        int expired = requests.expireStale();
        if (expired > 0) {
            log.info("Expired {} unserved travel request(s)", expired);
        }
    }
}
