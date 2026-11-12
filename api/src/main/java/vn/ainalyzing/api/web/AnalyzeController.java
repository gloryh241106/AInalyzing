package vn.ainalyzing.api.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.ainalyzing.api.analysis.AnalysisResult;
import vn.ainalyzing.api.analysis.AnalysisService;
import vn.ainalyzing.api.analysis.AnalysisSnapshot;
import vn.ainalyzing.api.analysis.AnalyzeRequest;
import vn.ainalyzing.api.analysis.SnapshotStore;
import vn.ainalyzing.api.csv.CsvException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * POST /api/analyze (Spec A.1 → A.2). The contract form is multipart: part "request" (JSON) and part "file" (CSV,
 * only for demandSource csv). A plain application/json body is also accepted for manual and demo runs. The CSV is
 * read into memory for this request only and never stored or logged (NFR-02).
 */
@RestController
public class AnalyzeController {

    /** NFR-02: 1 MiB. Spring's multipart limit enforces the same number before the controller runs. */
    static final long MAX_CSV_BYTES = 1024 * 1024;

    private final AnalyzeRequestReader reader;
    private final AnalysisService service;
    private final SnapshotStore store;

    public AnalyzeController(AnalyzeRequestReader reader, AnalysisService service, SnapshotStore store) {
        this.reader = reader;
        this.service = service;
        this.store = store;
    }

    @PostMapping(path = "/api/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AnalyzeResponse analyzeMultipart(@RequestPart("request") String request,
                                            @RequestPart(name = "file", required = false) MultipartFile file,
                                            HttpServletRequest http) {
        return run(request, file, http);
    }

    @PostMapping(path = "/api/analyze", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AnalyzeResponse analyzeJson(@RequestBody String request, HttpServletRequest http) {
        return run(request, null, http);
    }

    private AnalyzeResponse run(String json, MultipartFile file, HttpServletRequest http) {
        AnalyzeRequest req = reader.read(json);
        String csv = null;
        if (req.demandSource() == AnalyzeRequest.DemandSource.CSV) {
            if (file == null) {
                throw ApiException.validation("file", "Chọn tệp CSV khi nguồn sức bán là csv");
            }
            csv = decode(file);
        }
        AnalysisResult result = service.analyze(req, csv);
        AnalysisSnapshot snapshot = store.save(http, result);
        return AnalyzeResponse.of(snapshot);
    }

    /** BR-03: UTF-8 only. A byte sequence that is not UTF-8 is a CSV error, not silently replaced characters. */
    private static String decode(MultipartFile file) {
        if (file.getSize() > MAX_CSV_BYTES) {
            throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, List.of(
                    new ApiError(ApiExceptionHandler.FILE_TOO_LARGE, "Tệp CSV vượt quá 1 MiB", "file", null)));
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(file.getBytes()))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new CsvException(List.of(new CsvException.Problem("CSV_INVALID", "Tệp phải mã hóa UTF-8", null)));
        } catch (IOException e) {
            throw new CsvException(List.of(new CsvException.Problem("CSV_INVALID", "Không đọc được tệp", null)));
        }
    }
}
