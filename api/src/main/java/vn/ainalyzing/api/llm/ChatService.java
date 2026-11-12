package vn.ainalyzing.api.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import vn.ainalyzing.api.analysis.AnalysisSnapshot;
import vn.ainalyzing.api.analysis.Scenario;
import vn.ainalyzing.api.llm.FactBook.Rendered;
import vn.ainalyzing.api.llm.GroqClient.Completion;
import vn.ainalyzing.api.llm.GroqClient.ToolCall;
import vn.ainalyzing.api.llm.Template.Group;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Chat Orchestrator (LLM Architecture §2–§4, FR-10). One turn:
 * <ol>
 *   <li>The caller has passed the Scope Gate (snapshot of this session, not expired).</li>
 *   <li>Facts of the scenario on screen suffice → one strict-schema request. The question names another scenario →
 *       a request offering get_analysis_facts; the Tool Dispatcher checks the call, the Facts Tool answers with that
 *       scenario's facts under the same tool_call_id, and a second request returns the plan. At most one tool round,
 *       two Groq requests and groq.timeout-ms for the whole turn.</li>
 *   <li>The plan is validated against Java; the Renderer writes the text from Java values.</li>
 *   <li>Any failure while the snapshot is still valid → the local template answer (status fallback).</li>
 * </ol>
 * Numbers, costs and decisions always come from Java, whichever path answers.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int MAX_TOOL_CALLS = 3;

    private final GroqProperties props;
    private final GroqClient client;
    private final GroqGate gate;
    private final PromptBuilder prompts;
    private final ObjectMapper mapper;

    public ChatService(GroqProperties props, GroqClient client, GroqGate gate, PromptBuilder prompts, ObjectMapper mapper) {
        this.props = props;
        this.client = client;
        this.gate = gate;
        this.prompts = prompts;
        this.mapper = mapper;
    }

    /**
     * @param stillValid Scope Gate re-check, called before a tool runs and before the answer is returned
     * @throws StaleAnalysisException when the snapshot stopped being valid during the turn
     */
    public ChatResponse answer(AnalysisSnapshot snapshot, String rawQuestion, Scenario onScreen, BooleanSupplier stillValid) {
        String question = PromptBuilder.cleanQuestion(rawQuestion);
        FactBook book = new FactBook(snapshot.result());
        long start = System.nanoTime();
        int[] calls = {0};

        FallbackReason reason;
        if (!props.enabled()) {
            reason = FallbackReason.DISABLED;
        } else if (!props.egressAllowed(book.source())) {
            reason = FallbackReason.LOCAL_POLICY;
        } else {
            try {
                ChatResponse.Answer answer = viaGroq(book, question, onScreen, stillValid, start, calls);
                gateCheck(stillValid);
                String status = answer.intent() == Group.UNSUPPORTED ? ChatResponse.UNSUPPORTED : ChatResponse.OK;
                logTurn(status, null, calls[0], start);
                return new ChatResponse(snapshot.id(), status, null, answer);
            } catch (GroqException e) {
                reason = e.reason();
                if (reason == FallbackReason.RATE_LIMITED && e.retryAfter() != null) {
                    gate.pause(e.retryAfter());
                }
            }
        }
        gateCheck(stillValid);
        logTurn(ChatResponse.FALLBACK, reason, calls[0], start);
        return new ChatResponse(snapshot.id(), ChatResponse.FALLBACK, reason, local(book, question, onScreen));
    }

    /** Local template path: keyword intent, named or on-screen scenario, the one template Java says applies. */
    static ChatResponse.Answer local(FactBook book, String question, Scenario onScreen) {
        Group intent = IntentClassifier.intent(question);
        Scenario named = IntentClassifier.namedScenario(question);
        Scenario s = named == null ? onScreen : named;
        Rendered r = TemplateCatalog.chatTemplate(intent, book, s).renderFor(book, s);
        return new ChatResponse.Answer(intent, s, r.text(), r.factIds());
    }

    private ChatResponse.Answer viaGroq(FactBook book, String question, Scenario onScreen, BooleanSupplier stillValid,
                                        long start, int[] calls) {
        acquire(calls);
        if (!IntentClassifier.mentionsOtherScenario(question, onScreen)) {
            Completion c = client.complete(prompts.chatStrict(book, onScreen, question), remaining(start), "chat");
            return plan(book, c.content());
        }

        ObjectNode first = prompts.chatWithTool(book, onScreen, question);
        Completion c1 = client.complete(first, remaining(start), "chat-tool");
        if (c1.toolCalls().isEmpty()) {
            return plan(book, c1.content());
        }
        List<String[]> results = new ArrayList<>();
        for (ToolCall call : dispatch(c1.toolCalls())) {
            gateCheck(stillValid);
            Scenario s = scenarioArg(call);
            log.info("chat tool {} scenario={}", PromptBuilder.TOOL_NAME, s);
            results.add(new String[]{call.id(), prompts.toolResult(book, s)});
        }
        acquire(calls);
        Completion c2 = client.complete(prompts.chatAfterTool(first, c1.message(), results), remaining(start), "chat-tool-result");
        if (!c2.toolCalls().isEmpty()) {
            throw new GroqException(FallbackReason.TOOL_FAILURE, "second tool round");
        }
        return plan(book, c2.content());
    }

    /** Tool Dispatcher: only get_analysis_facts, with an id, at most three calls in the one allowed round. */
    private List<ToolCall> dispatch(List<ToolCall> calls) {
        if (calls.size() > MAX_TOOL_CALLS) {
            throw new GroqException(FallbackReason.TOOL_FAILURE, "too many tool calls");
        }
        for (ToolCall c : calls) {
            if (!PromptBuilder.TOOL_NAME.equals(c.name()) || c.id() == null || c.id().isBlank()) {
                throw new GroqException(FallbackReason.TOOL_FAILURE, "unknown tool");
            }
            scenarioArg(c);
        }
        return calls;
    }

    /** Arguments must be exactly {"scenario": SLOW|BASE|FAST}; the server, not the model, supplies the analysis. */
    private Scenario scenarioArg(ToolCall c) {
        try {
            JsonNode args = mapper.readTree(c.arguments() == null ? "" : c.arguments());
            if (args == null || !args.isObject() || args.size() != 1 || !args.path("scenario").isString()) {
                throw new GroqException(FallbackReason.TOOL_FAILURE, "bad tool arguments");
            }
            return Scenario.valueOf(args.get("scenario").stringValue());
        } catch (JacksonException | IllegalArgumentException e) {
            throw new GroqException(FallbackReason.TOOL_FAILURE, "bad tool arguments");
        }
    }

    private ChatResponse.Answer plan(FactBook book, String content) {
        PlanReader reader = new PlanReader(mapper, book);
        JsonNode json = reader.json(content);
        Group intent = reader.chatIntent(json);
        Scenario s = reader.scenario(json);
        Template t = reader.template(json, intent, s);
        reader.factIds(json);
        Rendered r = t.renderFor(book, s);
        return new ChatResponse.Answer(intent, s, r.text(), r.factIds());
    }

    private void acquire(int[] calls) {
        if (!gate.tryAcquire()) {
            throw new GroqException(FallbackReason.RATE_LIMITED, "local budget");
        }
        calls[0]++;
    }

    private Duration remaining(long startNanos) {
        long left = props.timeoutMs() - (System.nanoTime() - startNanos) / 1_000_000;
        if (left < 200) {
            throw new GroqException(FallbackReason.TIMEOUT, "deadline passed");
        }
        return Duration.ofMillis(left);
    }

    private static void gateCheck(BooleanSupplier stillValid) {
        if (!stillValid.getAsBoolean()) {
            throw new StaleAnalysisException();
        }
    }

    private void logTurn(String status, FallbackReason reason, int calls, long start) {
        log.info("chat status={} reason={} groqCalls={} ms={} model={} prompt={}", status, reason, calls,
                (System.nanoTime() - start) / 1_000_000, props.model(), PromptBuilder.PROMPT_VERSION);
    }
}
