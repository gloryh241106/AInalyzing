package vn.ainalyzing.api.analysis;

import java.time.DayOfWeek;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/** MON–SUN codes used on the wire and in fact ids (Spec: "thứ trong tuần MON–SUN"). */
public final class Weekdays {

    private static final String[] VI = {"Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ Nhật"};

    private Weekdays() {
    }

    public static String code(DayOfWeek d) {
        return d.name().substring(0, 3);
    }

    public static String vietnamese(DayOfWeek d) {
        return VI[d.getValue() - 1];
    }

    /** Re-keys an enum map by MON–SUN, keeping Monday-first order. */
    public static <V, R> Map<String, R> byCode(Map<DayOfWeek, V> in, Function<V, R> f) {
        Map<String, R> out = new LinkedHashMap<>();
        for (DayOfWeek d : DayOfWeek.values()) {
            if (in.containsKey(d)) {
                out.put(code(d), f.apply(in.get(d)));
            }
        }
        return out;
    }
}
