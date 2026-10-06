package technology.renatus.routepulse.driving.movement;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/driver/movement/{sessionId}")
public class MovementMonitoringController {
    private final MovementMonitoringService monitoring;
    public MovementMonitoringController(MovementMonitoringService monitoring) { this.monitoring=monitoring; }
    private ResponseEntity<MovementMonitoringService.Snapshot> result(MovementMonitoringService.Snapshot snapshot) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(snapshot);
    }
    @GetMapping public ResponseEntity<?> status(Authentication auth,@PathVariable Long sessionId) {
        return result(monitoring.status(auth,sessionId));
    }
    @PostMapping("/enable") public ResponseEntity<?> enable(Authentication auth,@PathVariable Long sessionId) {
        return result(monitoring.enable(auth,sessionId));
    }
    @PostMapping("/location") public ResponseEntity<?> location(Authentication auth,@PathVariable Long sessionId,@RequestBody LocationSample sample) {
        return result(monitoring.location(auth,sessionId,sample));
    }
    @PostMapping("/signal-lost") public ResponseEntity<?> signalLost(Authentication auth,@PathVariable Long sessionId) {
        return result(monitoring.signalLost(auth,sessionId));
    }
    public record Reply(MovementMonitoringService.Response response) {}
    @PostMapping("/response") public ResponseEntity<?> respond(Authentication auth,@PathVariable Long sessionId,@RequestBody Reply reply) {
        if (reply.response()==null) throw new IllegalArgumentException("Choose waiting, on break, or end session.");
        return result(monitoring.respond(auth,sessionId,reply.response()));
    }
    @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<?> invalid(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(java.util.Map.of("message",ex.getMessage()));
    }
    @ExceptionHandler(IllegalStateException.class) ResponseEntity<?> conflict(IllegalStateException ex) {
        return ResponseEntity.status(409).body(java.util.Map.of("message",ex.getMessage()));
    }
}
