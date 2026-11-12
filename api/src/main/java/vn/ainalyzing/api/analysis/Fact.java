package vn.ainalyzing.api.analysis;

/**
 * One Java-computed number or code (Spec Phụ lục B). Groq only ever sees {id, value, unit}; it may cite ids but
 * never supplies values. scenario is null for facts that do not depend on the demand series.
 *
 * @param value a Long, Double (one decimal) or String code
 */
public record Fact(String id, Object value, String unit, Scenario scenario) {

    public static final String VND = "VND";
    public static final String VND_PER_UNIT = "VND/hũ";
    public static final String UNITS = "hũ";
    public static final String UNITS_PER_DAY = "hũ/ngày";
    public static final String DAYS = "ngày";
    public static final String PERCENT = "%";
    public static final String CODE = "mã";

    public long longValue() {
        return ((Number) value).longValue();
    }
}
