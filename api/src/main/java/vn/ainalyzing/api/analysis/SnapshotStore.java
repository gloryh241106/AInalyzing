package vn.ainalyzing.api.analysis;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Keeps snapshots inside the caller's HTTP session (cookie JSESSIONID). A snapshot is found only through the session
 * that created it, so an analysisId copied into another browser is simply unknown (AC-08d) and nothing tells the
 * caller whether it exists elsewhere. Reset invalidates the session, which drops every snapshot and cached answer.
 * Data lives in memory only; a restart makes old ids stale, which the UI handles by asking for Tính again.
 */
@Component
public class SnapshotStore {

    static final String ATTRIBUTE = "ainalyzing.snapshots";
    /** Bounds memory per session; the oldest snapshot is dropped first. */
    static final int MAX_PER_SESSION = 10;

    private final Duration ttl;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public SnapshotStore(@Value("${app.analysis.ttl:30m}") Duration ttl, Clock clock) {
        this.ttl = ttl;
        this.clock = clock;
    }

    public AnalysisSnapshot save(HttpServletRequest request, AnalysisResult result) {
        Instant now = clock.instant();
        AnalysisSnapshot snapshot = new AnalysisSnapshot(newId(), now, now.plus(ttl), result);
        Map<String, AnalysisSnapshot> map = snapshots(request.getSession(true));
        synchronized (map) {
            map.values().removeIf(s -> s.expiredAt(now));
            map.put(snapshot.id(), snapshot);
            Iterator<String> oldest = map.keySet().iterator();
            while (map.size() > MAX_PER_SESSION) {
                oldest.next();
                oldest.remove();
            }
        }
        return snapshot;
    }

    /** Empty when there is no session, the id belongs to another session, or the snapshot has expired. */
    public Optional<AnalysisSnapshot> find(HttpServletRequest request, String analysisId) {
        HttpSession session = request.getSession(false);
        if (session == null || analysisId == null) {
            return Optional.empty();
        }
        Map<String, AnalysisSnapshot> map;
        try {
            map = snapshots(session);
        } catch (IllegalStateException invalidated) {
            return Optional.empty();
        }
        synchronized (map) {
            AnalysisSnapshot s = map.get(analysisId);
            if (s == null) {
                return Optional.empty();
            }
            if (s.expiredAt(clock.instant())) {
                map.remove(analysisId);
                return Optional.empty();
            }
            return Optional.of(s);
        }
    }

    /** Scope Gate re-check before a tool runs or an answer is returned. */
    public boolean stillValid(HttpServletRequest request, AnalysisSnapshot snapshot) {
        return find(request, snapshot.id()).isPresent();
    }

    public void reset(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            try {
                session.invalidate();
            } catch (IllegalStateException alreadyInvalid) {
                // nothing left to clear
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, AnalysisSnapshot> snapshots(HttpSession session) {
        // Session facades differ per request, so creation is guarded by this store rather than the session object.
        synchronized (this) {
            Object existing = session.getAttribute(ATTRIBUTE);
            if (existing != null) {
                return (Map<String, AnalysisSnapshot>) existing;
            }
            Map<String, AnalysisSnapshot> created = new LinkedHashMap<>();
            session.setAttribute(ATTRIBUTE, created);
            return created;
        }
    }

    private String newId() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
