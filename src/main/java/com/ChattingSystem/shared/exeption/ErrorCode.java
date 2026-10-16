package com.ChattingSystem.shared.exeption;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Chưa xác thực"),
    INVALID_FIREBASE_TOKEN(HttpStatus.UNAUTHORIZED, "Firebase ID Token không hợp lệ"),
    FIREBASE_EMAIL_REQUIRED(HttpStatus.BAD_REQUEST, "Firebase không cung cấp email"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email đã tồn tại"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Không có quyền truy cập"),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Yêu cầu không đúng định dạng"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Phương thức không được hỗ trợ"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"),
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy bài viết"),
    POST_SLUG_EXISTS(HttpStatus.CONFLICT, "Slug bài viết đã tồn tại"),
    INVALID_FILE(HttpStatus.BAD_REQUEST, "Tệp không hợp lệ. Chỉ chấp nhận ảnh JPEG, PNG, WebP, AVIF hoặc GIF"),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Tệp vượt quá dung lượng cho phép (5 MB)"),
    STORAGE_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "Chưa cấu hình dịch vụ lưu trữ"),
    STORAGE_ERROR(HttpStatus.BAD_GATEWAY, "Không thể tải tệp lên dịch vụ lưu trữ"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
