package vn.ainalyzing.api.llm;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.ainalyzing.api.analysis.Scenario;
import vn.ainalyzing.api.llm.Template.Group;

import java.util.ArrayList;
import java.util.List;

/**
 * Answer Validator (LLM Architecture §2). Reads the model's JSON and checks it against the snapshot: known enums,
 * a template of the right group that Java says applies to that scenario, and fact ids that exist in this analysis.
 * Any mismatch throws INVALID_OUTPUT and the turn falls back to the local template.
 */
final class PlanReader {

    private final ObjectMapper mapper;
    private final FactBook book;

    PlanReader(ObjectMapper mapper, FactBook book) {
        this.mapper = mapper;
        this.book = book;
    }

    JsonNode json(String content) {
        if (content == null || content.isBlank()) {
            throw invalid("empty content");
        }
        String s = content.strip();
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw invalid("no JSON object");
        }
        try {
            JsonNode n = mapper.readTree(s.substring(start, end + 1));
            if (!n.isObject()) {
                throw invalid("not an object");
            }
            return n;
        } catch (JacksonException e) {
            throw invalid("unparseable JSON");
        }
    }

    Scenario scenario(JsonNode node) {
        String v = text(node, "scenario");
        try {
            return Scenario.valueOf(v);
        } catch (IllegalArgumentException e) {
            throw invalid("bad scenario");
        }
    }

    Scenario scenario(JsonNode node, String field) {
        String v = text(node, field);
        try {
            return Scenario.valueOf(v);
        } catch (IllegalArgumentException e) {
            throw invalid("bad " + field);
        }
    }

    Group chatIntent(JsonNode node) {
        try {
            Group g = Group.valueOf(text(node, "intent"));
            if (!g.isChatIntent()) {
                throw invalid("intent is not a chat intent");
            }
            return g;
        } catch (IllegalArgumentException e) {
            throw invalid("bad intent");
        }
    }

    /** The template must exist, belong to the group, and apply to this snapshot and scenario. */
    Template template(JsonNode node, Group group, Scenario s) {
        Template t = TemplateCatalog.byId(text(node, "templateId")).orElseThrow(() -> invalid("unknown template"));
        if (t.group() != group) {
            throw invalid("template of another group");
        }
        if (!t.appliesTo(book, t.perScenario() ? s : Scenario.BASE)) {
            throw invalid("template does not fit the Java result");
        }
        return t;
    }

    /** Every cited fact must exist in this analysis (AC-08c: a reference to a missing fact is rejected). */
    List<String> factIds(JsonNode node) {
        JsonNode arr = node.get("factIds");
        if (arr == null || !arr.isArray()) {
            throw invalid("factIds missing");
        }
        List<String> ids = new ArrayList<>();
        for (JsonNode x : arr) {
            if (!x.isString() || !book.has(x.stringValue())) {
                throw invalid("unknown fact id");
            }
            ids.add(x.stringValue());
        }
        return ids;
    }

    JsonNode array(JsonNode node, String field) {
        JsonNode arr = node.get(field);
        if (arr == null || !arr.isArray()) {
            throw invalid(field + " missing");
        }
        return arr;
    }

    JsonNode object(JsonNode node, String field) {
        JsonNode o = node.get(field);
        if (o == null || !o.isObject()) {
            throw invalid(field + " missing");
        }
        return o;
    }

    String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || !v.isString()) {
            throw invalid(field + " missing");
        }
        return v.stringValue();
    }

    static GroqException invalid(String detail) {
        return new GroqException(FallbackReason.INVALID_OUTPUT, detail);
    }
}
