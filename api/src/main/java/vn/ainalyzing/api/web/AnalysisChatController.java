package vn.ainalyzing.api.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.ainalyzing.api.analysis.AnalysisSnapshot;
import vn.ainalyzing.api.analysis.Scenario;
import vn.ainalyzing.api.analysis.SnapshotStore;
import vn.ainalyzing.api.llm.ChatService;
import vn.ainalyzing.api.llm.InsightService;
import vn.ainalyzing.api.llm.StaleAnalysisException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Chat API + Scope Gate (LLM Architecture §2). The analysisId only selects among this session's snapshots; an id
 * from another session, an expired one and a reset one all look the same: 404 {"status":"stale_analysis"} with no
 * facts and no Groq call (AC-08d).
 */
@RestController
public class AnalysisChatController {

    public record InsightRequest(String analysisId) {
    }

    /** scenario (optional, default BASE) is the scenario the buyer is looking at. */
    public record ChatRequest(String analysisId, String question, String scenario) {
    }

    private final SnapshotStore store;
    private final InsightService insights;
    private final ChatService chat;

    public AnalysisChatController(SnapshotStore store, InsightService insights, ChatService chat) {
        this.store = store;
        this.insights = insights;
        this.chat = chat;
    }

    @PostMapping("/api/analysis/insight")
    public ResponseEntity<?> insight(@RequestBody InsightRequest body, HttpServletRequest http) {
        if (body == null || blank(body.analysisId())) {
            throw ApiException.validation("analysisId", "Bắt buộc");
        }
        Optional<AnalysisSnapshot> snapshot = store.find(http, body.analysisId());
        if (snapshot.isEmpty()) {
            return stale();
        }
        var card = insights.insight(snapshot.get());
        return store.stillValid(http, snapshot.get()) ? ResponseEntity.ok(card) : stale();
    }

    @PostMapping("/api/analysis/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest body, HttpServletRequest http) {
        List<ApiError> errors = new ArrayList<>();
        if (body == null || blank(body.analysisId())) {
            errors.add(ApiError.field("analysisId", "Bắt buộc"));
        }
        if (body == null || blank(body.question())) {
            errors.add(ApiError.field("question", "Nhập câu hỏi"));
        }
        Scenario onScreen = Scenario.BASE;
        if (body != null && !blank(body.scenario())) {
            try {
                onScreen = Scenario.valueOf(body.scenario());
            } catch (IllegalArgumentException e) {
                errors.add(ApiError.field("scenario", "Phải là SLOW, BASE hoặc FAST"));
            }
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        Optional<AnalysisSnapshot> snapshot = store.find(http, body.analysisId());
        if (snapshot.isEmpty()) {
            return stale();
        }
        AnalysisSnapshot s = snapshot.get();
        return ResponseEntity.ok(chat.answer(s, body.question(), onScreen, () -> store.stillValid(http, s)));
    }

    @ExceptionHandler(StaleAnalysisException.class)
    ResponseEntity<Map<String, String>> staleDuringTurn(StaleAnalysisException e) {
        return stale();
    }

    private static ResponseEntity<Map<String, String>> stale() {
        return ResponseEntity.status(404).body(Map.of("status", "stale_analysis"));
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
