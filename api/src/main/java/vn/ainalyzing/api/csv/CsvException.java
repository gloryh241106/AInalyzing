package vn.ainalyzing.api.csv;

import java.util.List;

/**
 * Carries every problem found in one file so the user can fix them all at once.
 * code is CSV_INVALID or INSUFFICIENT_SAMPLES (Spec Phụ lục A); row is the file line (header = 1) or null.
 */
public class CsvException extends RuntimeException {

    public record Problem(String code, String message, Integer row) {
    }

    private final List<Problem> problems;

    public CsvException(List<Problem> problems) {
        super(problems.getFirst().message());
        this.problems = List.copyOf(problems);
    }

    public List<Problem> problems() {
        return problems;
    }
}
