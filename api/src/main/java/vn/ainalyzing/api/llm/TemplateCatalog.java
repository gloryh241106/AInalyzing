package vn.ainalyzing.api.llm;

import vn.ainalyzing.api.analysis.Scenario;
import vn.ainalyzing.api.analysis.ScenarioResult.BlockReason;
import vn.ainalyzing.api.analysis.ScenarioResult.Conditions;
import vn.ainalyzing.api.analysis.ScenarioResult.OptionResult;
import vn.ainalyzing.api.analysis.ScenarioResult.Winner;
import vn.ainalyzing.api.analysis.Weekdays;
import vn.ainalyzing.api.llm.FactBook.Rendered;
import vn.ainalyzing.api.llm.FactBook.Text;
import vn.ainalyzing.api.llm.Template.Group;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static vn.ainalyzing.api.llm.FactBook.id;

/**
 * Answer templates (LLM Architecture §3, Answer Renderer). Groq returns only template ids, scenarios and fact ids;
 * every sentence the buyer reads is written here from Java values.
 * <p>
 * Within each chat intent group (WHY_OPTION … UNSUPPORTED) exactly one template applies to a given snapshot and
 * scenario, so the validator can check the model's choice against Java and the fallback path never has to guess.
 */
public final class TemplateCatalog {

    private static final List<Template> ALL = new ArrayList<>();
    private static final Map<String, Template> BY_ID = new LinkedHashMap<>();

    private TemplateCatalog() {
    }

    public static List<Template> all() {
        return List.copyOf(ALL);
    }

    public static Optional<Template> byId(String id) {
        return Optional.ofNullable(id == null ? null : BY_ID.get(id));
    }

    public static List<Template> group(Group g) {
        return ALL.stream().filter(t -> t.group() == g).toList();
    }

    public static List<Template> applicable(Group g, FactBook book, Scenario s) {
        return ALL.stream().filter(t -> t.group() == g && t.appliesTo(book, s)).toList();
    }

    /** The single template of a chat intent group that fits this scenario. */
    public static Template chatTemplate(Group intent, FactBook book, Scenario s) {
        List<Template> fit = applicable(intent, book, s);
        if (fit.size() != 1) {
            throw new IllegalStateException(intent + " has " + fit.size() + " templates for " + s);
        }
        return fit.getFirst();
    }

    // ---------------------------------------------------------------- helpers

    private static void add(String id, Group g, String kind, String hint, boolean perScenario,
                            java.util.function.BiPredicate<FactBook, Scenario> applies,
                            java.util.function.BiFunction<FactBook, Scenario, Rendered> render) {
        Template t = new Template(id, g, kind, hint, perScenario, applies, render);
        ALL.add(t);
        BY_ID.put(id, t);
    }

    private static String other(String option) {
        return "A".equals(option) ? "B" : "A";
    }

    private static boolean both(FactBook b, Scenario s) {
        return b.r(s).a().eligible() && b.r(s).b().eligible();
    }

    private static boolean decided(FactBook b, Scenario s) {
        Winner w = b.r(s).winner();
        return w == Winner.A || w == Winner.B;
    }

    private static Conditions cond(FactBook b, Scenario s) {
        return b.r(s).conditions();
    }

    private static boolean over(FactBook b, String option) {
        return b.has("F_" + option + "_OVER_BUDGET");
    }

    private static boolean pending(FactBook b, String option) {
        return !b.has("F_" + option + "_DAYS");
    }

    /** Why a quote is out of the ranking, as a clause: "có tổng giá …, vượt ngân sách … là …". */
    private static Text blocked(Text t, FactBook b, String option) {
        OptionResult o = b.option(Scenario.BASE, option);
        if (o.blockReason() == BlockReason.OVER_BUDGET) {
            return t.s("có tổng giá ").v("F_" + option + "_COST").s(", vượt ngân sách ").v("F_BUDGET")
                    .s(" là ").v("F_" + option + "_OVER_BUDGET");
        }
        return t.s("chưa xác nhận hạn bán nên chưa được xếp hạng");
    }

    private static Optional<DayOfWeek> worstStockoutDay(FactBook b) {
        DayOfWeek worst = null;
        long max = 1;
        for (DayOfWeek d : DayOfWeek.values()) {
            String id = "F_" + Weekdays.code(d) + "_STOCKOUT_DAYS";
            if (b.has(id) && b.num(id) > max) {
                max = b.num(id);
                worst = d;
            }
        }
        return Optional.ofNullable(worst);
    }

    private static long trend(FactBook b) {
        return b.has("F_TREND_4W_PCT") ? b.num("F_TREND_4W_PCT") : 0;
    }

    private static Text scenario(Text t, Scenario s) {
        return t.s("Ở kịch bản ").s(s.label()).s(", ");
    }

    static {
        // ------------------------------------------------ WHY_OPTION (chat) and recommendation (insight card)
        add("WINNER_BETTER_MARGIN", Group.WHY_OPTION, null, "both quotes eligible, the winner keeps more money", true,
                (b, s) -> both(b, s) && decided(b, s) && b.r(s).marginGapVnd() > 0,
                (b, s) -> {
                    String w = b.r(s).winner().name();
                    String l = other(w);
                    return scenario(b.text(), s).s("nên chọn đơn ").s(w).s(": khoản còn lại sau tiền nhập là ")
                            .v(id(w, "MARGIN", s)).s(", so với ").v(id(l, "MARGIN", s)).s(" của đơn ").s(l)
                            .s(" (hơn ").v("F_MARGIN_GAP_" + s).s("). Đơn ").s(w).s(" bán ").v(id(w, "SOLD", s))
                            .s(", hết hạn ").v(id(w, "EXPIRED", s)).s("; đơn ").s(l).s(" bán ").v(id(l, "SOLD", s))
                            .s(", hết hạn ").v(id(l, "EXPIRED", s)).s(".").done();
                });
        add("WINNER_EQUAL_MARGIN_CHEAPER", Group.WHY_OPTION, null, "same money left, the winner costs less", true,
                (b, s) -> both(b, s) && decided(b, s) && b.r(s).marginGapVnd() == 0,
                (b, s) -> {
                    String w = b.r(s).winner().name();
                    String l = other(w);
                    return scenario(b.text(), s).s("hai đơn có cùng khoản còn lại ").v(id(w, "MARGIN", s))
                            .s("; chọn đơn ").s(w).s(" vì tổng giá thấp hơn (").v("F_" + w + "_COST").s(" so với ")
                            .v("F_" + l + "_COST").s(").").done();
                });
        add("WINNER_TIE", Group.WHY_OPTION, null, "same money left and same cost", true,
                (b, s) -> b.r(s).winner() == Winner.TIE,
                (b, s) -> scenario(b.text(), s).s("hai đơn có cùng khoản còn lại ").v(id("A", "MARGIN", s))
                        .s(" và cùng tổng giá ").v("F_A_COST").s("; chọn đơn nào cũng như nhau.").done());
        add("ONLY_ONE_ELIGIBLE", Group.WHY_OPTION, null, "only one quote is allowed (budget or pending shelf life)", true,
                (b, s) -> b.r(s).a().eligible() != b.r(s).b().eligible(),
                (b, s) -> {
                    String w = b.r(s).a().eligible() ? "A" : "B";
                    String l = other(w);
                    Text t = scenario(b.text(), s).s("chỉ đơn ").s(w).s(" dùng được: đơn ").s(l).s(" ");
                    return blocked(t, b, l).s(". Đơn ").s(w).s(" bán ").v(id(w, "SOLD", s)).s(", khoản còn lại ")
                            .v(id(w, "MARGIN", s)).s(".").done();
                });
        add("NONE_ELIGIBLE", Group.WHY_OPTION, null, "neither quote is allowed", true,
                (b, s) -> b.r(s).winner() == Winner.NONE,
                (b, s) -> {
                    Text t = b.text().s("Chưa có đơn nào dùng được: đơn A ");
                    blocked(t, b, "A").s("; đơn B ");
                    return blocked(t, b, "B").s(". Hãy bổ sung hạn bán hoặc điều chỉnh ngân sách rồi bấm Tính lại.").done();
                });

        // ------------------------------------------------ WHAT_IF_PRICE
        add("PRICE_NO_BASELINE", Group.WHAT_IF_PRICE, null, "quote A cannot be the baseline", true,
                (b, s) -> cond(b, s) == null,
                (b, s) -> blocked(b.text().s("Đơn A "), b, "A")
                        .s(", nên chưa tính được giá tối đa cho đơn B. Hãy sửa đơn A rồi bấm Tính lại.").done());
        add("PRICE_PENDING", Group.WHAT_IF_PRICE, null, "B shelf life not confirmed yet", true,
                (b, s) -> cond(b, s) != null && cond(b, s).pending(),
                (b, s) -> {
                    Text t = b.text().s("Hạn bán của lô B chưa xác nhận nên chưa tính được ngưỡng giá. ");
                    if (b.has(id("B", "MIN_DAYS", s))) {
                        t = scenario(t, s).s("ở giá đang báo ").v("F_B_COST").s(", lô B cần còn ít nhất ")
                                .v(id("B", "MIN_DAYS", s)).s(" bán để hơn đơn A.");
                    } else if (over(b, "B")) {
                        t = t.s("Tổng giá B ").v("F_B_COST").s(" cũng vượt ngân sách ").v("F_BUDGET").s(".");
                    } else {
                        t = scenario(t, s).s("dù còn 14 ngày, đơn B vẫn không hơn đơn A ở giá đang báo ").v("F_B_COST").s(".");
                    }
                    return t.done();
                });
        add("PRICE_NOT_FEASIBLE", Group.WHAT_IF_PRICE, null, "no total price lets B beat A within budget", true,
                (b, s) -> cond(b, s) != null && !cond(b, s).pending() && cond(b, s).maxWinningTotalVnd() == null,
                (b, s) -> scenario(b.text(), s).s("đơn B ngang đơn A khi tổng giá là ").v("F_BREAKEVEN_TOTAL_" + s)
                        .s(", nhưng không có mức tổng giá khả thi nào (sau phí giao, trong ngân sách ").v("F_BUDGET")
                        .s(") giúp B hơn A.").done());
        add("PRICE_THRESHOLD", Group.WHAT_IF_PRICE, null, "break-even and maximum winning total for B", true,
                (b, s) -> cond(b, s) != null && !cond(b, s).pending() && cond(b, s).maxWinningTotalVnd() != null,
                (b, s) -> {
                    Text t = scenario(b.text(), s).s("đơn B ngang đơn A khi tổng giá B là ").v("F_BREAKEVEN_TOTAL_" + s)
                            .s(". Để B hơn A mà vẫn trong ngân sách ").v("F_BUDGET").s(", tổng giá B tối đa là ")
                            .v("F_MAX_WIN_TOTAL_" + s).s("; giá đang báo là ").v("F_B_COST").s(".");
                    boolean reached = b.num("F_B_COST") <= b.num("F_MAX_WIN_TOTAL_" + s);
                    return t.s(reached ? " Giá đang báo đã đạt mức này." : " Giá đang báo cao hơn mức này nên cần thương lượng giảm giá.").done();
                });

        // ------------------------------------------------ WHAT_IF_BUDGET (does not depend on the scenario)
        add("BUDGET_A_OVER", Group.WHAT_IF_BUDGET, null, "budget does not cover quote A", true,
                (b, s) -> over(b, "A"),
                (b, s) -> {
                    Text t = b.text().s("Ngân sách ").v("F_BUDGET").s(" không đủ cho đơn A (").v("F_A_COST").s(")");
                    if (over(b, "B")) {
                        t.s(" lẫn đơn B (").v("F_B_COST").s(")");
                    }
                    return t.s(". Tăng ngân sách hoặc xin giảm giá trong form rồi bấm Tính lại.").done();
                });
        add("BUDGET_B_OVER", Group.WHAT_IF_BUDGET, null, "budget covers A but not B", true,
                (b, s) -> !over(b, "A") && over(b, "B"),
                (b, s) -> b.text().s("Đơn B cần ").v("F_B_COST").s(", vượt ngân sách ").v("F_BUDGET").s(" là ")
                        .v("F_B_OVER_BUDGET").s(", nên chỉ xét đơn A (").v("F_A_COST")
                        .s("). Muốn xét B, tăng ngân sách hoặc xin giảm giá trong form rồi bấm Tính lại.").done());
        add("BUDGET_BOTH_FIT", Group.WHAT_IF_BUDGET, null, "budget covers both quotes", true,
                (b, s) -> !over(b, "A") && !over(b, "B"),
                (b, s) -> b.text().s("Cả hai đơn đều nằm trong ngân sách ").v("F_BUDGET").s(" (A ").v("F_A_COST")
                        .s(", B ").v("F_B_COST").s("), nên ngân sách không quyết định lựa chọn ở lượt này. "
                                + "Muốn thử ngân sách khác, sửa trong form rồi bấm Tính lại.").done());

        // ------------------------------------------------ WHAT_IF_SHELF_LIFE
        add("SHELF_NO_BASELINE", Group.WHAT_IF_SHELF_LIFE, null, "quote A cannot be the baseline", true,
                (b, s) -> cond(b, s) == null,
                (b, s) -> blocked(b.text().s("Đơn A "), b, "A")
                        .s(", nên chưa tính được hạn bán tối thiểu cho đơn B. Hãy sửa đơn A rồi bấm Tính lại.").done());
        add("SHELF_OVER_BUDGET", Group.WHAT_IF_SHELF_LIFE, null, "B is over budget, more shelf life does not help", true,
                (b, s) -> cond(b, s) != null && over(b, "B"),
                (b, s) -> b.text().s("Đơn B vượt ngân sách ").v("F_BUDGET").s(" là ").v("F_B_OVER_BUDGET")
                        .s(", nên thêm hạn bán cũng chưa đủ để chọn B.").done());
        add("SHELF_MIN_DAYS", Group.WHAT_IF_SHELF_LIFE, null, "minimum sellable days for B to beat A at the quoted price", true,
                (b, s) -> cond(b, s) != null && !over(b, "B") && cond(b, s).minSellableDaysAtQuotedPrice() != null,
                (b, s) -> {
                    Text t = scenario(b.text(), s).s("ở giá đang báo ").v("F_B_COST").s(", lô B cần còn ít nhất ")
                            .v(id("B", "MIN_DAYS", s)).s(" bán để hơn đơn A.");
                    if (pending(b, "B")) {
                        t.s(" Hạn lô B chưa xác nhận, hãy hỏi nhà cung cấp.");
                    } else {
                        t.s(" Lô B hiện còn ").v("F_B_DAYS").s(".");
                    }
                    return t.done();
                });
        add("SHELF_NOT_ENOUGH", Group.WHAT_IF_SHELF_LIFE, null, "even 14 days does not let B beat A", true,
                (b, s) -> cond(b, s) != null && !over(b, "B") && cond(b, s).minSellableDaysAtQuotedPrice() == null,
                (b, s) -> scenario(b.text(), s).s("dù lô B còn đủ 14 ngày, đơn B vẫn không hơn đơn A ở giá đang báo ")
                        .v("F_B_COST").s("; cần thương lượng giá.").done());

        // ------------------------------------------------ UNSUPPORTED
        add("UNSUPPORTED", Group.UNSUPPORTED, null, "question is outside this analysis", true,
                (b, s) -> true,
                (b, s) -> b.text().s("Mình chỉ giải thích lượt tính đang xem: vì sao chọn đơn A hay B, giá tối đa cho đơn B, "
                        + "ngân sách và hạn bán tối thiểu. Bạn thử hỏi một trong các ý đó nhé.").done());

        // ------------------------------------------------ SCENARIO_REASON (insight card)
        add("SCENARIO_BASE_DEFAULT", Group.SCENARIO_REASON, null, "BASE: no strong signal in the sales history", true,
                (b, s) -> s == Scenario.BASE,
                (b, s) -> {
                    Text t = b.text().s("Xem kịch bản Cơ sở trước: ");
                    switch (b.source()) {
                        case "csv" -> t.s("mức bán là trung vị theo thứ của ").v("F_DAYS_IN_STOCK").s(" còn hàng trong lịch sử");
                        case "manual" -> t.s("đây là mức bán nhập tay ở giữa");
                        default -> t.s("đây là mức bán giữa của ca demo (dữ liệu giả lập)");
                    }
                    return t.s(", tổng nhu cầu 14 ngày là ").v("F_DEMAND_14D_BASE").s(".").done();
                });
        add("SCENARIO_FAST_STOCKOUT", Group.SCENARIO_REASON, null, "FAST: stock-outs hide part of demand", true,
                (b, s) -> s == Scenario.FAST && b.has("F_DAYS_STOCKOUT") && b.num("F_DAYS_STOCKOUT") >= 3,
                (b, s) -> {
                    Text t = b.text().s("Xem thêm kịch bản Nhanh: lịch sử có ").v("F_DAYS_STOCKOUT").s(" hết hàng");
                    worstStockoutDay(b).ifPresent(d -> {
                        String c = Weekdays.code(d);
                        t.s(", nhiều nhất vào ").s(Weekdays.vietnamese(d)).s(" (").n("F_" + c + "_STOCKOUT_DAYS")
                                .s("/").n("F_" + c + "_DAYS").s(" ngày)");
                    });
                    return t.s(", nên số bán ghi nhận có thể thấp hơn nhu cầu thật.").done();
                });
        add("SCENARIO_FAST_TREND", Group.SCENARIO_REASON, null, "FAST: last 4 weeks sell clearly more", true,
                (b, s) -> s == Scenario.FAST && trend(b) >= 10,
                (b, s) -> b.text().s("Xem thêm kịch bản Nhanh: 4 tuần gần nhất bán nhiều hơn 4 tuần trước ")
                        .v("F_TREND_4W_PCT").s(".").done());
        add("SCENARIO_SLOW_TREND", Group.SCENARIO_REASON, null, "SLOW: last 4 weeks sell clearly less", true,
                (b, s) -> s == Scenario.SLOW && trend(b) <= -10,
                (b, s) -> b.text().s("Xem thêm kịch bản Chậm: số bán 4 tuần gần nhất thay đổi ").v("F_TREND_4W_PCT")
                        .s(" so với 4 tuần trước.").done());

        // ------------------------------------------------ INSIGHT (insight card)
        add("INS_B_PENDING", Group.INSIGHT, "SHELF_LIFE", "B shelf life is not confirmed", false,
                (b, s) -> pending(b, "B"),
                (b, s) -> {
                    Text t = b.text().s("Hạn lô B chưa xác nhận nên B đứng ngoài xếp hạng");
                    if (b.has("F_B_MIN_DAYS_BASE")) {
                        t.s("; ở kịch bản Cơ sở và giá đang báo, lô B cần còn ít nhất ").v("F_B_MIN_DAYS_BASE").s(" bán");
                    }
                    return t.s(".").done();
                });
        add("INS_B_OVER_BUDGET", Group.INSIGHT, "BUDGET", "B is over budget", false,
                (b, s) -> over(b, "B"),
                (b, s) -> b.text().s("Đơn B ").v("F_B_COST").s(" vượt ngân sách ").v("F_BUDGET").s(" là ")
                        .v("F_B_OVER_BUDGET").s(" nên đứng ngoài xếp hạng.").done());
        add("INS_WINNER_CHANGES", Group.INSIGHT, "SCENARIO_CHANGE", "the choice changes between scenarios", false,
                (b, s) -> Arrays.stream(Scenario.values()).map(x -> b.r(x).winner()).distinct().count() > 1,
                (b, s) -> b.text().s("Lựa chọn đổi theo sức bán: Chậm chọn ").winner("F_WINNER_SLOW").s(", Cơ sở chọn ")
                        .winner("F_WINNER_BASE").s(", Nhanh chọn ").winner("F_WINNER_FAST").s(".").done());
        add("INS_WINNER_STABLE", Group.INSIGHT, "SCENARIO_CHANGE", "all scenarios pick the same quote", false,
                (b, s) -> Arrays.stream(Scenario.values()).map(x -> b.r(x).winner()).distinct().count() == 1
                        && decided(b, Scenario.BASE),
                (b, s) -> b.text().s("Cả ba kịch bản đều chọn ").winner("F_WINNER_BASE")
                        .s(", nên kết luận không phụ thuộc vào sức bán.").done());
        add("INS_STOCKOUT_WEEKDAY", Group.INSIGHT, "STOCKOUT", "one weekday often sells out", false,
                (b, s) -> worstStockoutDay(b).isPresent(),
                (b, s) -> {
                    DayOfWeek d = worstStockoutDay(b).orElseThrow();
                    String c = Weekdays.code(d);
                    Text t = b.text().s(Weekdays.vietnamese(d)).s(" hết hàng ").n("F_" + c + "_STOCKOUT_DAYS").s("/")
                            .n("F_" + c + "_DAYS").s(" ngày trong lịch sử");
                    if (b.has("F_" + c + "_AVG_IN_STOCK")) {
                        t.s("; ngày còn hàng bán trung bình ").v("F_" + c + "_AVG_IN_STOCK");
                    }
                    return t.s(".").done();
                });
        add("INS_TREND", Group.INSIGHT, "TREND", "sales trend of the last 4 weeks", false,
                (b, s) -> b.has("F_TREND_4W_PCT") && Math.abs(b.num("F_TREND_4W_PCT")) >= 5,
                (b, s) -> b.text().s("Số bán ngày còn hàng của 4 tuần gần nhất thay đổi ").v("F_TREND_4W_PCT")
                        .s(" so với 4 tuần trước.").done());
        add("INS_ANOMALY", Group.INSIGHT, "ANOMALY", "unusually high sales days", false,
                (b, s) -> b.has("F_ANOMALY_COUNT"),
                (b, s) -> b.text().s("Có ").v("F_ANOMALY_COUNT").s(" bán cao bất thường so với cùng thứ, cao nhất ")
                        .v("F_ANOMALY_MAX_UNITS").s("; mức Cơ sở dùng trung vị nên ít bị kéo lên.").done());
        add("INS_NEGATIVE_MARGIN", Group.INSIGHT, "LOSS", "a quote loses money in this scenario", true,
                (b, s) -> negative(b.r(s).a()) || negative(b.r(s).b()),
                (b, s) -> {
                    Text t = scenario(b.text(), s);
                    boolean a = negative(b.r(s).a());
                    boolean bb = negative(b.r(s).b());
                    if (a) {
                        t.s("đơn A lỗ, khoản còn lại ").v(id("A", "MARGIN", s));
                    }
                    if (a && bb) {
                        t.s("; ");
                    }
                    if (bb) {
                        t.s("đơn B lỗ, khoản còn lại ").v(id("B", "MARGIN", s));
                    }
                    return t.s(".").done();
                });
        add("INS_B_EXPIRY", Group.INSIGHT, "EXPIRY", "part of B expires before it sells", true,
                (b, s) -> b.has(id("B", "EXPIRED", s)) && b.num(id("B", "EXPIRED", s)) > 0,
                (b, s) -> scenario(b.text(), s).s("đơn B còn ").v(id("B", "EXPIRED", s)).s(" hết hạn trong số ")
                        .v("F_B_UNITS").s(" nhập.").done());
        add("INS_A_SHORTAGE", Group.INSIGHT, "SHORTAGE", "A runs out before demand ends", true,
                (b, s) -> b.has(id("A", "SHORT", s)) && b.num(id("A", "SHORT", s)) > 0,
                (b, s) -> scenario(b.text(), s).s("đơn A thiếu ").v(id("A", "SHORT", s)).s(" so với nhu cầu ")
                        .v("F_DEMAND_14D_" + s).s(" trong 14 ngày.").done());

        // ------------------------------------------------ SUPPLIER_QUESTION (insight card)
        add("Q_PRICE", Group.SUPPLIER_QUESTION, null, "ask for a total price at or below the maximum winning total", true,
                (b, s) -> cond(b, s) != null && !cond(b, s).pending() && cond(b, s).maxWinningTotalVnd() != null
                        && b.num("F_B_COST") > cond(b, s).maxWinningTotalVnd(),
                (b, s) -> b.text().s("Có thể giao ").v("F_B_UNITS").s(" với tổng giá không quá ")
                        .v("F_MAX_WIN_TOTAL_" + s).s(" không?").done());
        add("Q_SHELF_LIFE", Group.SUPPLIER_QUESTION, null, "ask whether B has the minimum sellable days", true,
                (b, s) -> cond(b, s) != null && cond(b, s).minSellableDaysAtQuotedPrice() != null
                        && (pending(b, "B") || b.num("F_B_DAYS") < cond(b, s).minSellableDaysAtQuotedPrice()),
                (b, s) -> b.text().s("Lô ").v("F_B_UNITS").s(" còn bán được bao nhiêu ngày tính từ ngày nhận? Cần ít nhất ")
                        .v(id("B", "MIN_DAYS", s)).s(" để đơn B có lợi hơn.").done());
        add("Q_CONFIRM_SHELF_LIFE", Group.SUPPLIER_QUESTION, null, "ask B's shelf life (no minimum known)", false,
                (b, s) -> pending(b, "B") && (cond(b, Scenario.BASE) == null
                        || cond(b, Scenario.BASE).minSellableDaysAtQuotedPrice() == null),
                (b, s) -> b.text().s("Lô ").v("F_B_UNITS").s(" của đơn B còn bán được bao nhiêu ngày tính từ ngày nhận?").done());
    }

    private static boolean negative(OptionResult o) {
        return o.marginVnd() != null && o.marginVnd() < 0;
    }
}
