package vn.ainalyzing.api.web;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.ainalyzing.api.analysis.AnalyzeRequest;
import vn.ainalyzing.api.analysis.AnalyzeRequest.DemandSource;
import vn.ainalyzing.api.analysis.AnalyzeRequest.ManualDemand;
import vn.ainalyzing.api.analysis.Quote;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * FR-02 / BR-01 / BR-02 / Spec A.1. Reads the JSON tree field by field instead of binding a class, so one wrong
 * type does not hide the other mistakes: every bad field comes back with its path (for example quoteB.units) and the
 * buyer keeps the rest of the form. A quote whose shelf life is null stays valid (the "pending" state).
 */
@Component
public class AnalyzeRequestReader {

    /** Upper bounds keep every product (units × price) far inside a long. */
    static final long MAX_VND = 1_000_000_000_000L;
    static final long MAX_UNITS = 1_000_000;
    static final int MAX_SKU = 40;
    static final int MAX_DAYS = 14;

    private final ObjectMapper mapper;

    public AnalyzeRequestReader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public AnalyzeRequest read(String json) {
        JsonNode root;
        try {
            root = json == null ? null : mapper.readTree(json);
        } catch (JacksonException e) {
            root = null;
        }
        if (root == null || !root.isObject()) {
            throw ApiException.validation(List.of(new ApiError(ApiError.VALIDATION_ERROR,
                    "Phần request phải là một đối tượng JSON", null, null)));
        }
        Fields f = new Fields(root);

        String sku = f.text("sku", 1, MAX_SKU);
        LocalDate receiveDate = f.date("receiveDate");
        Long sellPrice = f.integer("sellPriceVnd", true, 1, MAX_VND);
        Long budget = f.integer("budgetVnd", true, 0, MAX_VND);

        Long openingUnits = 0L;
        Long openingDays = null;
        JsonNode opening = root.get("openingStock");
        if (opening != null && !opening.isNull()) {
            if (!opening.isObject()) {
                f.error("openingStock", "Phải là đối tượng {units, sellableDays}");
                openingUnits = null;
            } else {
                openingUnits = f.integer("openingStock.units", true, 0, MAX_UNITS);
                openingDays = f.integer("openingStock.sellableDays", false, 1, MAX_DAYS);
                if (openingUnits != null && openingUnits > 0 && openingDays == null && !f.hasError("openingStock.sellableDays")) {
                    f.error("openingStock.sellableDays", "Có tồn đầu kỳ thì phải nhập số ngày còn bán (1–14)");
                }
            }
        }

        Quote quoteA = quote(f, "quoteA");
        Quote quoteB = quote(f, "quoteB");
        // Compared on the units alone so this error shows even when another field of a quote is wrong (AC-01).
        Long unitsA = f.validUnits("quoteA.units");
        Long unitsB = f.validUnits("quoteB.units");
        if (unitsA != null && unitsB != null && unitsB <= unitsA) {
            f.error("quoteB.units", "Số lượng B phải lớn hơn số lượng A");
        }

        DemandSource source = null;
        String sourceText = f.text("demandSource", 1, 10);
        if (sourceText != null) {
            source = switch (sourceText) {
                case "csv" -> DemandSource.CSV;
                case "manual" -> DemandSource.MANUAL;
                case "demo" -> DemandSource.DEMO;
                default -> {
                    f.error("demandSource", "Phải là csv, manual hoặc demo");
                    yield null;
                }
            };
        }

        ManualDemand manual = null;
        if (source == DemandSource.MANUAL) {
            manual = manualDemand(f);
        }
        String demoCode = null;
        if (source == DemandSource.DEMO) {
            demoCode = f.text("demoCaseCode", 1, 40);
        }

        if (!f.errors.isEmpty()) {
            throw ApiException.validation(f.errors);
        }
        int openUnits = openingUnits.intValue();
        return new AnalyzeRequest(sku.trim(), receiveDate, sellPrice, budget,
                openUnits, openUnits > 0 ? openingDays.intValue() : null,
                quoteA, quoteB, source, manual, demoCode);
    }

    private static Quote quote(Fields f, String name) {
        JsonNode node = f.root.get(name);
        if (node == null || node.isNull()) {
            f.error(name, "Bắt buộc");
            return null;
        }
        if (!node.isObject()) {
            f.error(name, "Phải là đối tượng {units, goodsVnd, deliveryVnd, sellableDays}");
            return null;
        }
        Long units = f.integer(name + ".units", true, 1, MAX_UNITS);
        Long goods = f.integer(name + ".goodsVnd", true, 0, MAX_VND);
        Long delivery = f.integer(name + ".deliveryVnd", true, 0, MAX_VND);
        Long days = f.integer(name + ".sellableDays", false, 1, MAX_DAYS);
        if (units == null || goods == null || delivery == null || f.hasError(name + ".sellableDays")) {
            return null;
        }
        if (goods + delivery > MAX_VND) {
            f.error(name + ".goodsVnd", "Tổng tiền hàng và phí giao quá lớn");
            return null;
        }
        return new Quote(units.intValue(), goods, delivery, days == null ? null : days.intValue());
    }

    private static ManualDemand manualDemand(Fields f) {
        JsonNode node = f.root.get("manualDemand");
        if (node == null || node.isNull()) {
            f.error("manualDemand", "Bắt buộc khi nhập tay sức bán");
            return null;
        }
        if (!node.isObject()) {
            f.error("manualDemand", "Phải là đối tượng {slow, base, fast}");
            return null;
        }
        Long slow = f.integer("manualDemand.slow", true, 0, MAX_UNITS);
        Long base = f.integer("manualDemand.base", true, 0, MAX_UNITS);
        Long fast = f.integer("manualDemand.fast", true, 0, MAX_UNITS);
        if (slow == null || base == null || fast == null) {
            return null;
        }
        boolean ordered = true;
        if (slow > base) {
            f.error("manualDemand.slow", "Mức chậm không được lớn hơn mức cơ sở");
            ordered = false;
        }
        if (base > fast) {
            f.error("manualDemand.fast", "Mức nhanh không được nhỏ hơn mức cơ sở");
            ordered = false;
        }
        return ordered ? new ManualDemand(slow.intValue(), base.intValue(), fast.intValue()) : null;
    }

    /** Reads dotted paths from the root and collects errors in field order. */
    private static final class Fields {
        final JsonNode root;
        final List<ApiError> errors = new ArrayList<>();

        Fields(JsonNode root) {
            this.root = root;
        }

        void error(String path, String message) {
            errors.add(ApiError.field(path, message));
        }

        boolean hasError(String path) {
            return errors.stream().anyMatch(e -> path.equals(e.field()));
        }

        /** The value at path if it is a valid unit count (already checked by integer()), without adding errors. */
        Long validUnits(String path) {
            JsonNode n = node(path);
            if (n == null || hasError(path) || !n.isIntegralNumber() || !n.canConvertToLong()) {
                return null;
            }
            return n.longValue();
        }

        JsonNode node(String path) {
            JsonNode n = root;
            for (String part : path.split("\\.")) {
                n = n == null ? null : n.get(part);
            }
            return n == null || n.isNull() ? null : n;
        }

        String text(String path, int min, int max) {
            JsonNode n = node(path);
            if (n == null) {
                error(path, "Bắt buộc");
                return null;
            }
            if (!n.isString()) {
                error(path, "Phải là chuỗi");
                return null;
            }
            String s = n.stringValue().trim();
            if (s.length() < min || s.length() > max) {
                error(path, "Độ dài phải từ " + min + " đến " + max + " ký tự");
                return null;
            }
            return s;
        }

        LocalDate date(String path) {
            JsonNode n = node(path);
            if (n == null) {
                error(path, "Bắt buộc");
                return null;
            }
            try {
                if (n.isString() && n.stringValue().length() == 10) {
                    return LocalDate.parse(n.stringValue());
                }
            } catch (DateTimeParseException e) {
                // reported below
            }
            error(path, "Ngày phải có dạng YYYY-MM-DD");
            return null;
        }

        /** Null when missing (and not required) or invalid; the error is recorded in the second case. */
        Long integer(String path, boolean required, long min, long max) {
            JsonNode n = node(path);
            if (n == null) {
                if (required) {
                    error(path, "Bắt buộc");
                }
                return null;
            }
            if (!n.isIntegralNumber() || !n.canConvertToLong()) {
                error(path, "Phải là số nguyên");
                return null;
            }
            long v = n.longValue();
            if (v < min || v > max) {
                error(path, range(min, max));
                return null;
            }
            return v;
        }

        private static String range(long min, long max) {
            if (max == MAX_DAYS) {
                return "Phải từ " + min + " đến " + max + " ngày";
            }
            return min == 0 ? "Không được âm (tối đa " + max + ")" : "Phải lớn hơn 0 (tối đa " + max + ")";
        }
    }
}
