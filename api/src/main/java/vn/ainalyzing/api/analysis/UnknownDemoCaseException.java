package vn.ainalyzing.api.analysis;

/** demoCaseCode does not exist in demo_cases; reported as VALIDATION_ERROR on that field. */
public class UnknownDemoCaseException extends RuntimeException {

    public UnknownDemoCaseException(String caseCode) {
        super("Unknown demo case");
    }
}
