package vn.ainalyzing.api.demo;

import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

@RestController
public class DemoController {

    private final DemoCaseRepository repository;

    public DemoController(DemoCaseRepository repository) {
        this.repository = repository;
    }

    /** Spec A: 200 [{caseCode, title, request}] or 503 DB_UNAVAILABLE. request_json is already valid JSON. */
    @GetMapping(value = "/api/demo", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> list() {
        try {
            String body = repository.findAll().stream()
                    .map(c -> "{\"caseCode\":" + quote(c.caseCode()) + ",\"title\":" + quote(c.title())
                            + ",\"request\":" + c.requestJson() + "}")
                    .collect(Collectors.joining(",", "[", "]"));
            return ResponseEntity.ok(body);
        } catch (DataAccessException e) {
            return ResponseEntity.status(503).body(
                    "{\"errors\":[{\"code\":\"DB_UNAVAILABLE\",\"message\":\"Không đọc được ca demo\",\"field\":null,\"row\":null}]}");
        }
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
