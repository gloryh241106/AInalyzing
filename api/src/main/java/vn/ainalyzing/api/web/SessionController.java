package vn.ainalyzing.api.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.ainalyzing.api.analysis.SnapshotStore;

/** FR-09 / UC-06 Đặt lại: drops the session, its snapshots and cached answers. Always 204. */
@RestController
public class SessionController {

    private final SnapshotStore store;

    public SessionController(SnapshotStore store) {
        this.store = store;
    }

    @PostMapping("/api/session/reset")
    public ResponseEntity<Void> reset(HttpServletRequest request) {
        store.reset(request);
        return ResponseEntity.noContent().build();
    }
}
