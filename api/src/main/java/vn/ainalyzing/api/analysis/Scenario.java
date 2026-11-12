package vn.ainalyzing.api.analysis;

/** The three demand series (Spec A.2 scenarios.SLOW / BASE / FAST). */
public enum Scenario {
    SLOW("Chậm"), BASE("Cơ sở"), FAST("Nhanh");

    private final String label;

    Scenario(String label) {
        this.label = label;
    }

    /** Vietnamese label shown to the buyer. */
    public String label() {
        return label;
    }
}
