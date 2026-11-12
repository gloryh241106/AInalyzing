package vn.ainalyzing.api.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import vn.ainalyzing.api.analysis.UnknownDemoCaseException;
import vn.ainalyzing.api.csv.CsvException;

import java.util.List;

/**
 * Maps every failure to the Spec error body. Logs only the error code and class (the request id is in the log
 * pattern); never request bodies, CSV content or questions (Convention §4).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    public static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";
    public static final String DB_UNAVAILABLE = "DB_UNAVAILABLE";

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError.Body> api(ApiException e) {
        return body(e.status(), e.errors());
    }

    /** 422 CSV_INVALID / INSUFFICIENT_SAMPLES, or 413 when the file breaks the 366-row limit (NFR-02). */
    @ExceptionHandler(CsvException.class)
    ResponseEntity<ApiError.Body> csv(CsvException e) {
        List<ApiError> errors = e.problems().stream()
                .map(p -> new ApiError(p.code(), p.message(), "file", p.row()))
                .toList();
        boolean tooLarge = errors.stream().anyMatch(x -> FILE_TOO_LARGE.equals(x.code()));
        return body(tooLarge ? HttpStatus.CONTENT_TOO_LARGE : HttpStatus.UNPROCESSABLE_CONTENT, errors);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError.Body> tooLarge(MaxUploadSizeExceededException e) {
        return body(HttpStatus.CONTENT_TOO_LARGE,
                List.of(new ApiError(FILE_TOO_LARGE, "Tệp CSV vượt quá 1 MiB", "file", null)));
    }

    @ExceptionHandler(UnknownDemoCaseException.class)
    ResponseEntity<ApiError.Body> unknownDemo(UnknownDemoCaseException e) {
        return body(HttpStatus.BAD_REQUEST, List.of(ApiError.field("demoCaseCode", "Không có ca demo này")));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ApiError.Body> missingPart(MissingServletRequestPartException e) {
        return body(HttpStatus.BAD_REQUEST, List.of(ApiError.field(e.getRequestPartName(), "Bắt buộc")));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MultipartException.class})
    ResponseEntity<ApiError.Body> unreadable(Exception e) {
        return body(HttpStatus.BAD_REQUEST, List.of(new ApiError(ApiError.VALIDATION_ERROR,
                "Nội dung yêu cầu không đọc được (JSON hoặc multipart sai định dạng)", null, null)));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError.Body> mediaType(HttpMediaTypeNotSupportedException e) {
        return body(HttpStatus.UNSUPPORTED_MEDIA_TYPE, List.of(new ApiError("UNSUPPORTED_MEDIA_TYPE",
                "Dùng multipart/form-data hoặc application/json", null, null)));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError.Body> method(HttpRequestMethodNotSupportedException e) {
        return body(HttpStatus.METHOD_NOT_ALLOWED,
                List.of(new ApiError("METHOD_NOT_ALLOWED", "Phương thức không được hỗ trợ", null, null)));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError.Body> notFound(NoResourceFoundException e) {
        return body(HttpStatus.NOT_FOUND, List.of(new ApiError("NOT_FOUND", "Không có đường dẫn này", null, null)));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiError.Body> database(DataAccessException e) {
        log.warn("database unavailable: {}", e.getClass().getSimpleName());
        return body(HttpStatus.SERVICE_UNAVAILABLE,
                List.of(new ApiError(DB_UNAVAILABLE, "Không đọc được ca demo, thử lại sau", null, null)));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError.Body> unexpected(Exception e) {
        log.error("unexpected error: {}", e.getClass().getName(), e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR,
                List.of(new ApiError("INTERNAL_ERROR", "Lỗi máy chủ, thử lại sau", null, null)));
    }

    private static ResponseEntity<ApiError.Body> body(HttpStatus status, List<ApiError> errors) {
        return ResponseEntity.status(status).body(new ApiError.Body(errors));
    }
}
