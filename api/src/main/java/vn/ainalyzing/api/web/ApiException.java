package vn.ainalyzing.api.web;

import org.springframework.http.HttpStatus;

import java.util.List;

/** Carries an HTTP status and every error found, so the buyer can fix all fields at once (FR-02). */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final List<ApiError> errors;

    public ApiException(HttpStatus status, List<ApiError> errors) {
        super(errors.isEmpty() ? status.toString() : errors.getFirst().code());
        this.status = status;
        this.errors = List.copyOf(errors);
    }

    public static ApiException validation(List<ApiError> errors) {
        return new ApiException(HttpStatus.BAD_REQUEST, errors);
    }

    public static ApiException validation(String field, String message) {
        return validation(List.of(ApiError.field(field, message)));
    }

    public HttpStatus status() {
        return status;
    }

    public List<ApiError> errors() {
        return errors;
    }
}
