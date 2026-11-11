package vn.ainalyzing.api.csv;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** BR-03: UTF-8, comma separated, header date,units_sold,stock_status, one SKU, dates unique and before D. */
public final class SalesCsvParser {

    public static final String HEADER = "date,units_sold,stock_status";
    public static final int MAX_ROWS = 366;
    private static final String CODE = "CSV_INVALID";

    private SalesCsvParser() {
    }

    public static List<SalesDay> parse(String text, LocalDate receiveDate) {
        String[] lines = text.replace("﻿", "").split("\r?\n");
        List<CsvException.Problem> problems = new ArrayList<>();
        if (lines.length == 0 || !lines[0].trim().equalsIgnoreCase(HEADER)) {
            throw new CsvException(List.of(new CsvException.Problem(CODE, "Dòng tiêu đề phải là " + HEADER, 1)));
        }

        List<SalesDay> days = new ArrayList<>();
        Set<LocalDate> seen = new HashSet<>();
        int dataRows = 0;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            int row = i + 1;
            if (++dataRows > MAX_ROWS) {
                problems.add(new CsvException.Problem(CODE, "Tệp có hơn " + MAX_ROWS + " dòng dữ liệu", row));
                break;
            }
            String[] cells = line.split(",", -1);
            if (cells.length != 3) {
                problems.add(new CsvException.Problem(CODE, "Cần đúng 3 cột", row));
                continue;
            }
            LocalDate date;
            try {
                date = LocalDate.parse(cells[0].trim());
            } catch (DateTimeParseException e) {
                problems.add(new CsvException.Problem(CODE, "Ngày phải có dạng YYYY-MM-DD", row));
                continue;
            }
            if (!date.isBefore(receiveDate)) {
                problems.add(new CsvException.Problem(CODE, "Ngày phải trước ngày nhận hàng", row));
                continue;
            }
            if (!seen.add(date)) {
                problems.add(new CsvException.Problem(CODE, "Ngày bị trùng", row));
                continue;
            }
            int units;
            try {
                units = Integer.parseInt(cells[1].trim());
            } catch (NumberFormatException e) {
                units = -1;
            }
            if (units < 0) {
                problems.add(new CsvException.Problem(CODE, "Số bán phải là số nguyên ≥ 0", row));
                continue;
            }
            SalesDay.Status status = switch (cells[2].trim()) {
                case "in_stock" -> SalesDay.Status.IN_STOCK;
                case "stockout" -> SalesDay.Status.STOCKOUT;
                case "unknown" -> SalesDay.Status.UNKNOWN;
                default -> null;
            };
            if (status == null) {
                problems.add(new CsvException.Problem(CODE, "Trạng thái phải là in_stock, stockout hoặc unknown", row));
                continue;
            }
            days.add(new SalesDay(date, units, status));
        }
        if (!problems.isEmpty()) {
            throw new CsvException(problems);
        }
        if (days.isEmpty()) {
            throw new CsvException(List.of(new CsvException.Problem(CODE, "Tệp không có dòng dữ liệu", null)));
        }
        return days;
    }
}
