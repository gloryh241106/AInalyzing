package vn.ainalyzing.api.llm;

import vn.ainalyzing.api.analysis.Scenario;
import vn.ainalyzing.api.llm.FactBook.Rendered;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;

/**
 * One answer template. applies() is decided by Java from the snapshot; render() fills Java values only.
 *
 * @param kind        insight kind shown to the UI (INSIGHT group only), otherwise null
 * @param hint        one-line description sent to the model
 * @param perScenario true when the template talks about one scenario (the model must name it)
 */
public record Template(String id, Group group, String kind, String hint, boolean perScenario,
                       BiPredicate<FactBook, Scenario> applies, BiFunction<FactBook, Scenario, Rendered> render) {

    public enum Group {
        WHY_OPTION, WHAT_IF_PRICE, WHAT_IF_BUDGET, WHAT_IF_SHELF_LIFE, UNSUPPORTED,
        SCENARIO_REASON, INSIGHT, SUPPLIER_QUESTION;

        /** Spec A.4 chat intents are the first five groups. */
        public boolean isChatIntent() {
            return ordinal() <= UNSUPPORTED.ordinal();
        }
    }

    public boolean appliesTo(FactBook book, Scenario s) {
        return applies.test(book, s);
    }

    public Rendered renderFor(FactBook book, Scenario s) {
        return render.apply(book, s);
    }
}
