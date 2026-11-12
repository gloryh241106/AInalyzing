package vn.ainalyzing.api.llm;

import org.junit.jupiter.api.Test;
import vn.ainalyzing.api.Fixtures;
import vn.ainalyzing.api.analysis.AnalysisResult;
import vn.ainalyzing.api.analysis.Quote;
import vn.ainalyzing.api.analysis.Scenario;
import vn.ainalyzing.api.llm.FactBook.Rendered;
import vn.ainalyzing.api.llm.Template.Group;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Renderer: one chat template per intent and scenario, every template renders from existing facts only. */
class TemplateCatalogTest {

    static List<AnalysisResult> cases() throws IOException {
        List<AnalysisResult> out = new ArrayList<>(List.of(Fixtures.std(), Fixtures.sixDays(), Fixtures.budget400k(),
                Fixtures.pending(), Fixtures.csv()));
        var svc = Fixtures.service();
        out.add(svc.analyze(Fixtures.withB(Fixtures.demo(5, 432_000), new Quote(48, 404_000, 0, 5)), null)); // tie on margin
        out.add(svc.analyze(Fixtures.demo(5, 100_000), null));                                              // both over budget
        out.add(svc.analyze(Fixtures.withA(Fixtures.demo(5, 432_000), new Quote(24, 284_000, 0, null)), null)); // A pending
        out.add(svc.analyze(Fixtures.withB(Fixtures.demo(5, 432_000), new Quote(48, 0, 432_000, 5)), null)); // all delivery
        out.add(svc.analyze(Fixtures.withB(Fixtures.demo(14, 432_000), new Quote(48, 200_000, 0, 14)), null)); // B cheap
        out.add(svc.analyze(Fixtures.withB(Fixtures.demo(null, 400_000), new Quote(48, 432_000, 0, null)), null)); // pending + over
        return out;
    }

    @Test
    void exactlyOneChatTemplatePerIntentAndScenario() throws IOException {
        for (AnalysisResult r : cases()) {
            FactBook book = new FactBook(r);
            for (Group g : Group.values()) {
                if (!g.isChatIntent()) {
                    continue;
                }
                for (Scenario s : Scenario.values()) {
                    assertEquals(1, TemplateCatalog.applicable(g, book, s).size(), g + " " + s);
                }
            }
        }
    }

    @Test
    void everyApplicableTemplateRendersWithKnownFacts() throws IOException {
        for (AnalysisResult r : cases()) {
            FactBook book = new FactBook(r);
            for (Template t : TemplateCatalog.all()) {
                for (Scenario s : Scenario.values()) {
                    if (t.appliesTo(book, s)) {
                        Rendered out = t.renderFor(book, s);
                        assertFalse(out.text().isBlank(), t.id());
                        assertFalse(out.text().contains("null"), t.id() + ": " + out.text());
                        out.factIds().forEach(id -> assertTrue(book.has(id), id));
                    }
                }
            }
        }
    }

    @Test
    void ac08aFiveAndSixDayAnswers() {
        FactBook five = new FactBook(Fixtures.std());
        Rendered r5 = TemplateCatalog.chatTemplate(Group.WHY_OPTION, five, Scenario.BASE).renderFor(five, Scenario.BASE);
        assertTrue(r5.text().contains("chọn đơn A") && r5.text().contains("76.000đ") && r5.text().contains("48.000đ")
                && r5.text().contains("28.000đ"), r5.text());

        FactBook six = new FactBook(Fixtures.sixDays());
        Rendered r6 = TemplateCatalog.chatTemplate(Group.WHY_OPTION, six, Scenario.BASE).renderFor(six, Scenario.BASE);
        assertTrue(r6.text().contains("chọn đơn B") && r6.text().contains("93.000đ") && r6.text().contains("17.000đ"), r6.text());
    }

    @Test
    void ac05ConditionsInWords() {
        FactBook five = new FactBook(Fixtures.std());
        String price = TemplateCatalog.chatTemplate(Group.WHAT_IF_PRICE, five, Scenario.BASE).renderFor(five, Scenario.BASE).text();
        assertTrue(price.contains("404.000đ") && price.contains("403.999đ") && price.contains("432.000đ"), price);

        FactBook pending = new FactBook(Fixtures.pending());
        Template shelf = TemplateCatalog.chatTemplate(Group.WHAT_IF_SHELF_LIFE, pending, Scenario.BASE);
        assertEquals("SHELF_MIN_DAYS", shelf.id());
        assertTrue(shelf.renderFor(pending, Scenario.BASE).text().contains("6 ngày"));

        FactBook budget = new FactBook(Fixtures.budget400k());
        String b = TemplateCatalog.chatTemplate(Group.WHAT_IF_BUDGET, budget, Scenario.BASE).renderFor(budget, Scenario.BASE).text();
        assertTrue(b.contains("32.000đ") && b.contains("400.000đ"), b);
    }

    @Test
    void numbersUseVietnameseFormat() {
        assertEquals("-27.000", FactBook.Text.number(-27_000L));
        assertEquals("5,7", FactBook.Text.number(5.666));
        assertEquals("1.234.567", FactBook.Text.number(1_234_567L));
    }
}
