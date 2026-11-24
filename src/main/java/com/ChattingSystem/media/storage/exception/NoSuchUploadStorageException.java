package com.ChattingSystem.media.storage.exception;

/**
 * Ngoại lệ ném ra khi Multipart UploadId không tồn tại hoặc đã bị hủy / hoàn tất trên Object Storage.
 */
public class NoSuchUploadStorageException extends RuntimeException {

    public NoSuchUploadStorageException(String message) {
        super(message);
    }

    public NoSuchUploadStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
