package vn.ainalyzing.api.demo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class DemoCaseRepository {

    /** requestJson is the A.1 request the form is filled with; the three arrays are 14-day demand. */
    public record DemoCase(String caseCode, String title, String requestJson,
                           int[] demandSlow, int[] demandBase, int[] demandFast) {
    }

    private final JdbcTemplate jdbc;

    public DemoCaseRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<DemoCase> findAll() {
        return jdbc.query("SELECT * FROM demo_cases ORDER BY case_code", (rs, n) -> map(rs));
    }

    public Optional<DemoCase> find(String caseCode) {
        return jdbc.query("SELECT * FROM demo_cases WHERE case_code = ?", (rs, n) -> map(rs), caseCode)
                .stream().findFirst();
    }

    private static DemoCase map(ResultSet rs) throws SQLException {
        return new DemoCase(rs.getString("case_code"), rs.getString("title"), rs.getString("request_json"),
                ints(rs.getArray("demand_slow")), ints(rs.getArray("demand_base")), ints(rs.getArray("demand_fast")));
    }

    private static int[] ints(Array a) throws SQLException {
        Integer[] boxed = (Integer[]) a.getArray();
        int[] out = new int[boxed.length];
        for (int i = 0; i < boxed.length; i++) {
            out[i] = boxed[i];
        }
        return out;
    }
}
