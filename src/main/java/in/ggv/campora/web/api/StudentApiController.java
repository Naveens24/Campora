package in.ggv.campora.web.api;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import in.ggv.campora.identity.security.CamporaUserDetails;
import in.ggv.campora.mobility.dto.StudentRequestView;
import in.ggv.campora.mobility.service.TravelRequestService;

/** JSON endpoints for students (used for live status refresh; mobile-ready). */
@RestController
@RequestMapping("/api/v1/student")
public class StudentApiController {

    private final TravelRequestService requests;

    public StudentApiController(TravelRequestService requests) {
        this.requests = requests;
    }

    /** 200 with the live request, or 204 when the student has none. */
    @GetMapping("/request")
    public ResponseEntity<StudentRequestView> activeRequest(@AuthenticationPrincipal CamporaUserDetails me) {
        return requests.activeRequest(me.userId())
                .map(view -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(view))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
