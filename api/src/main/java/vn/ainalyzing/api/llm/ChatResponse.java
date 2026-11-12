package vn.ainalyzing.api.llm;

import vn.ainalyzing.api.analysis.Scenario;
import vn.ainalyzing.api.llm.Template.Group;

import java.util.List;

/**
 * Spec A.4 {analysisId, status, answer:{intent, text, factIds}} plus answer.scenario and fallbackReason
 * (docs/decisions.md D-05).
 *
 * @param status         ok (Groq answer validated), unsupported (Groq said the question is out of scope) or fallback
 *                       (local template because Groq was not used or not usable; see fallbackReason)
 * @param fallbackReason null unless status is fallback
 */
public record ChatResponse(String analysisId, String status, FallbackReason fallbackReason, Answer answer) {

    public static final String OK = "ok";
    public static final String UNSUPPORTED = "unsupported";
    public static final String FALLBACK = "fallback";

    public record Answer(Group intent, Scenario scenario, String text, List<String> factIds) {
    }
}
