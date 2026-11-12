package vn.ainalyzing.api.llm;

import vn.ainalyzing.api.analysis.Scenario;
import vn.ainalyzing.api.llm.Template.Group;

import java.text.Normalizer;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Keyword rules used when Groq is not available (fallback path) and to decide whether a chat turn may need another
 * scenario's facts (tool path). Works on Vietnamese with or without diacritics. Whole-word matching keeps "gia"
 * (price) from matching "giai thich".
 */
public final class IntentClassifier {

    private static final Map<Group, List<String>> KEYWORDS = new EnumMap<>(Group.class);

    static {
        KEYWORDS.put(Group.WHY_OPTION, List.of("vi sao", "tai sao", "sao lai", "why", "chon", "nen nhap", "nen lay",
                "nen mua", "nen dat", "loi hon", "tot hon", "phuong an", "don nao", "a hay b", "b hay a", "giai thich",
                "khuyen nghi", "recommend", "which", "lo", "lai", "con lai", "loi nhuan", "doanh thu", "ban duoc",
                "bao nhieu hu", "margin", "profit"));
        KEYWORDS.put(Group.WHAT_IF_PRICE, List.of("gia", "price", "re hon", "giam gia", "tong gia", "chiet khau",
                "tra gia", "thuong luong", "mac ca", "bao nhieu tien"));
        KEYWORDS.put(Group.WHAT_IF_SHELF_LIFE, List.of("han", "het han", "han dung", "han ban", "ngay ban", "shelf",
                "expiry", "expire", "bao nhieu ngay", "may ngay", "con ban"));
        KEYWORDS.put(Group.WHAT_IF_BUDGET, List.of("ngan sach", "budget", "tien mat", "du tien", "von"));
    }

    private IntentClassifier() {
    }

    /** Highest keyword count wins; ties go to WHY_OPTION, then price, shelf life, budget. No match is UNSUPPORTED. */
    public static Group intent(String question) {
        String text = normalize(question);
        Group best = Group.UNSUPPORTED;
        int bestScore = 0;
        for (Map.Entry<Group, List<String>> e : KEYWORDS.entrySet()) {
            int score = 0;
            for (String k : e.getValue()) {
                if (text.contains(" " + k + " ")) {
                    score++;
                }
            }
            if (score > bestScore || (score == bestScore && score > 0 && priority(e.getKey()) < priority(best))) {
                best = e.getKey();
                bestScore = score;
            }
        }
        return best;
    }

    /** The first scenario named in the question, or null. */
    public static Scenario namedScenario(String question) {
        String text = normalize(question);
        int bestAt = Integer.MAX_VALUE;
        Scenario best = null;
        for (Map.Entry<Scenario, List<String>> e : Map.of(
                Scenario.SLOW, List.of("cham", "slow"),
                Scenario.BASE, List.of("co so", "base", "trung binh"),
                Scenario.FAST, List.of("nhanh", "fast")).entrySet()) {
            for (String k : e.getValue()) {
                int at = text.indexOf(" " + k + " ");
                if (at >= 0 && at < bestAt) {
                    bestAt = at;
                    best = e.getKey();
                }
            }
        }
        return best;
    }

    /** True when answering may need facts beyond the scenario on screen, so the request offers the tool. */
    public static boolean mentionsOtherScenario(String question, Scenario onScreen) {
        Scenario named = namedScenario(question);
        if (named != null && named != onScreen) {
            return true;
        }
        String text = normalize(question);
        for (String k : List.of("kich ban khac", "cac kich ban", "moi kich ban", "ca ba", "3 kich ban", "ba kich ban",
                "other scenario", "all scenarios")) {
            if (text.contains(" " + k + " ")) {
                return true;
            }
        }
        return false;
    }

    /** Lower case, no diacritics, đ → d, punctuation → spaces, padded with spaces for whole-word search. */
    static String normalize(String s) {
        String lower = s == null ? "" : s.toLowerCase(Locale.ROOT).replace('đ', 'd');
        String plain = Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return " " + plain.replaceAll("[^a-z0-9]+", " ").trim() + " ";
    }

    private static int priority(Group g) {
        return switch (g) {
            case WHY_OPTION -> 0;
            case WHAT_IF_PRICE -> 1;
            case WHAT_IF_SHELF_LIFE -> 2;
            case WHAT_IF_BUDGET -> 3;
            default -> 9;
        };
    }
}
