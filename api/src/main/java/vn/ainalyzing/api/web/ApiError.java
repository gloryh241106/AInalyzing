package vn.ainalyzing.api.web;

import java.util.List;

/** One entry of the Spec error body {"errors":[{code,message,field,row}]}; field and row are null when unused. */
public record ApiError(String code, String message, String field, Integer row) {

    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";

    public static ApiError field(String field, String message) {
        return new ApiError(VALIDATION_ERROR, message, field, null);
    }

    /** Wire shape of every error response. */
    public record Body(List<ApiError> errors) {
    }
}
