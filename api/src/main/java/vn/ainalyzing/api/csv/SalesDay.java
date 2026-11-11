package vn.ainalyzing.api.csv;

import java.time.LocalDate;

/** One CSV row (BR-03). */
public record SalesDay(LocalDate date, int unitsSold, Status status) {

    public enum Status { IN_STOCK, STOCKOUT, UNKNOWN }
}
