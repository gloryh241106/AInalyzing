package vn.ainalyzing.api.llm;

import vn.ainalyzing.api.analysis.AnalysisResult;
import vn.ainalyzing.api.analysis.Fact;
import vn.ainalyzing.api.analysis.Scenario;
import vn.ainalyzing.api.analysis.ScenarioResult;
import vn.ainalyzing.api.analysis.ScenarioResult.OptionResult;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Facts Tool + Answer Renderer helpers: read-only access to one snapshot's Java result and facts, and a text builder
 * that writes Java values (never model output) into Vietnamese sentences while recording which fact ids it used.
 */
public final class FactBook {

    private final AnalysisResult result;
    private final Map<String, Fact> byId = new LinkedHashMap<>();

    public FactBook(AnalysisResult result) {
        this.result = result;
        for (Fact f : result.facts()) {
            byId.put(f.id(), f);
        }
    }

    public AnalysisResult result() {
        return result;
    }

    public ScenarioResult r(Scenario s) {
        return result.scenario(s);
    }

    public OptionResult option(Scenario s, String name) {
        return "A".equals(name) ? r(s).a() : r(s).b();
    }

    public String source() {
        return result.demandSourceInfo().source();
    }

    public boolean has(String id) {
        return byId.containsKey(id);
    }

    public Fact fact(String id) {
        Fact f = byId.get(id);
        if (f == null) {
            throw new IllegalStateException("template used a missing fact " + id);
        }
        return f;
    }

    public long num(String id) {
        return fact(id).longValue();
    }

    public Set<String> ids() {
        return byId.keySet();
    }

    /** Facts that do not depend on the scenario plus the facts of scenario s (what one chat turn sends). */
    public List<Fact> forScenario(Scenario s) {
        return byId.values().stream().filter(f -> f.scenario() == null || f.scenario() == s).toList();
    }

    public List<Fact> all() {
        return List.copyOf(byId.values());
    }

    public Text text() {
        return new Text(this);
    }

    /** F_&lt;object&gt;_&lt;metric&gt;_&lt;scenario&gt;. */
    public static String id(String object, String metric, Scenario s) {
        return "F_" + object + "_" + metric + "_" + s.name();
    }

    /** Text produced by a template and the facts it shows. */
    public record Rendered(String text, List<String> factIds) {
    }

    public static final class Text {
        private final FactBook book;
        private final StringBuilder sb = new StringBuilder();
        private final Set<String> used = new LinkedHashSet<>();

        private Text(FactBook book) {
            this.book = book;
        }

        public Text s(String text) {
            sb.append(text);
            return this;
        }

        /** Appends the fact's value with its unit, e.g. "76.000đ", "24 hũ", "5 ngày". */
        public Text v(String id) {
            sb.append(format(book.fact(id)));
            used.add(id);
            return this;
        }

        /** Appends only the number, for forms such as "5/8 ngày". */
        public Text n(String id) {
            sb.append(number(book.fact(id).value()));
            used.add(id);
            return this;
        }

        /** Appends a winner code as words: "đơn A", "đơn B", "hòa", "không đơn nào". */
        public Text winner(String id) {
            String code = String.valueOf(book.fact(id).value());
            sb.append(switch (code) {
                case "A", "B" -> "đơn " + code;
                case "TIE" -> "hòa";
                default -> "không đơn nào";
            });
            used.add(id);
            return this;
        }

        public Rendered done() {
            return new Rendered(sb.toString(), List.copyOf(used));
        }

        static String format(Fact f) {
            String n = number(f.value());
            return switch (f.unit()) {
                case Fact.VND -> n + "đ";
                case Fact.VND_PER_UNIT -> n + "đ/hũ";
                case Fact.PERCENT -> n + "%";
                case Fact.CODE -> n;
                default -> n + " " + f.unit();
            };
        }

        static String number(Object value) {
            DecimalFormatSymbols symbols = new DecimalFormatSymbols();
            symbols.setGroupingSeparator('.');
            symbols.setDecimalSeparator(',');
            symbols.setMinusSign('-');
            if (value instanceof Double d) {
                return new DecimalFormat("#,##0.0", symbols).format(d);
            }
            if (value instanceof Number x) {
                return new DecimalFormat("#,##0", symbols).format(x.longValue());
            }
            return String.valueOf(value);
        }
    }
}
