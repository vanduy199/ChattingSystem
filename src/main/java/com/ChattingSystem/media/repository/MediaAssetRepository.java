package com.ChattingSystem.media.repository;

import com.ChattingSystem.media.entity.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    /**
     * Lấy khóa phân tán theo người dùng (Postgres advisory transaction lock).
     * Dùng dạng truy vấn SELECT 1 an toàn với các phiên bản Hibernate mới.
     * Tự động giải phóng khi transaction kết thúc (commit hoặc rollback).
     */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(:userId)) AS t", nativeQuery = true)
    Integer acquireUserAdvisoryLock(@Param("userId") Long userId);

    /**
     * Tra cứu tệp theo uploader và clientUploadId (hỗ trợ Idempotency khi gọi lại init).
     */
    Optional<MediaAsset> findByUploaderIdAndClientUploadId(Long uploaderId, UUID clientUploadId);

    /**
     * Đếm số lượt tải ảnh HD trong ngày tại phòng PUBLIC của người dùng.
     * JOIN với bảng chat_rooms để đảm bảo không tính nhầm lượt tải tại phòng private.
     */
    @Query("SELECT COUNT(a) FROM MediaAsset a, ChatRoom r WHERE a.roomId = r.id " +
           "AND a.uploaderId = :userId " +
           "AND a.quality = com.ChattingSystem.media.entity.MediaQuality.HD " +
           "AND a.createdAt >= :startOfDay " +
           "AND a.status NOT IN (com.ChattingSystem.media.entity.MediaStatus.FAILED, com.ChattingSystem.media.entity.MediaStatus.BLOCKED) " +
           "AND LOWER(r.roomType) = 'public'")
    long countHDInDay(@Param("userId") Long userId, @Param("startOfDay") Instant startOfDay);

    /**
     * Đếm tất cả bản ghi khởi tạo của uploaderId kể từ mốc thời gian since (phục vụ rate-limit 1 phút maxInitPerMinute).
     */
    @Query("SELECT COUNT(a) FROM MediaAsset a WHERE a.uploaderId = :userId AND a.createdAt >= :since")
    long countInitSince(@Param("userId") Long userId, @Param("since") Instant since);

    /**
     * Đếm số lượng phiên upload đang mở (CREATING, UPLOADING, COMPLETING) còn hạn (phục vụ maxOpenUploads).
     */
    @Query("SELECT COUNT(a) FROM MediaAsset a WHERE a.uploaderId = :userId " +
           "AND a.createdAt >= :notExpiredAfter " +
           "AND a.status IN (com.ChattingSystem.media.entity.MediaStatus.CREATING, " +
           "com.ChattingSystem.media.entity.MediaStatus.UPLOADING, " +
           "com.ChattingSystem.media.entity.MediaStatus.COMPLETING)")
    long countOpenUploads(@Param("userId") Long userId, @Param("notExpiredAfter") Instant notExpiredAfter);

    /**
     * Lấy danh sách toàn bộ tệp đính kèm sắp xếp cố định theo messageId và sortOrder (phục vụ lấy lịch sử chat, tránh N+1).
     */
    List<MediaAsset> findByMessageIdInOrderByMessageIdAscSortOrderAsc(Collection<Long> messageIds);

    /**
     * Gán tệp vào tin nhắn nguyên tử, chống race condition khi nhiều request cùng cố gắn 1 tệp.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE MediaAsset a SET a.messageId = :messageId, a.sortOrder = :sortOrder, a.updatedAt = :now " +
           "WHERE a.id = :assetId AND a.uploaderId = :uploaderId AND a.roomId = :roomId AND a.messageId IS NULL " +
           "AND a.status IN (com.ChattingSystem.media.entity.MediaStatus.PROCESSING, com.ChattingSystem.media.entity.MediaStatus.AVAILABLE)")
    int attachToMessageIfUnclaimed(@Param("assetId") Long assetId,
                                   @Param("messageId") Long messageId,
                                   @Param("sortOrder") int sortOrder,
                                   @Param("uploaderId") Long uploaderId,
                                   @Param("roomId") Long roomId,
                                   @Param("now") Instant now);

    /**
     * Kiểm tra các ID tệp có thuộc tin nhắn còn sống của phòng hay không (kiểm tra quyền truy cập URL).
     */
    @Query(value = """
            SELECT a.id FROM media_assets a
              JOIN messages m ON m.id = a.message_id
             WHERE m.room_id = :roomId AND m.deleted_at IS NULL
               AND a.id IN (:ids)
            """, nativeQuery = true)
    List<Long> findActiveAssetIdsInRoom(@Param("roomId") Long roomId, @Param("ids") Collection<Long> ids);

    /**
     * Atomic claim: chuyển trạng thái UPLOADING -> COMPLETING.
     * Cập nhật a.updatedAt = :now và kiểm tra điều kiện chưa hết hạn (a.createdAt >= :notExpiredAfter).
     * Trả về số dòng cập nhật (1 nếu claim thành công, 0 nếu bị request khác claim trước).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE MediaAsset a SET a.status = com.ChattingSystem.media.entity.MediaStatus.COMPLETING, a.updatedAt = :now " +
           "WHERE a.id = :id AND a.uploaderId = :uploaderId " +
           "AND a.status = com.ChattingSystem.media.entity.MediaStatus.UPLOADING " +
           "AND a.createdAt >= :notExpiredAfter")
    int claimForCompleting(@Param("id") Long id,
                           @Param("uploaderId") Long uploaderId,
                           @Param("now") Instant now,
                           @Param("notExpiredAfter") Instant notExpiredAfter);

    /**
     * Cập nhật trạng thái sang PROCESSING khi hoàn tất ghép mảnh và kiểm chứng an toàn thành công.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE MediaAsset a SET a.status = com.ChattingSystem.media.entity.MediaStatus.PROCESSING, " +
           "a.contentType = :contentType, a.updatedAt = :now, a.nextRunAt = :now, a.attempts = 0, a.lockedUntil = null " +
           "WHERE a.id = :id AND a.status = com.ChattingSystem.media.entity.MediaStatus.COMPLETING")
    int updateStatusToProcessing(@Param("id") Long id,
                                 @Param("contentType") String contentType,
                                 @Param("now") Instant now);

    /**
     * Hoàn lại trạng thái sang UPLOADING khi ghép mảnh thiếu hoặc storage lỗi tạm thời.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE MediaAsset a SET a.status = com.ChattingSystem.media.entity.MediaStatus.UPLOADING, a.updatedAt = :now " +
           "WHERE a.id = :id AND a.status = com.ChattingSystem.media.entity.MediaStatus.COMPLETING")
    int revertStatusToUploading(@Param("id") Long id, @Param("now") Instant now);

    /**
     * Đánh dấu BLOCKED khi phát hiện gian lận dung lượng hoặc chữ ký tệp không hợp lệ.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE MediaAsset a SET a.status = com.ChattingSystem.media.entity.MediaStatus.BLOCKED, " +
           "a.lastError = :reason, a.updatedAt = :now " +
           "WHERE a.id = :id AND a.status = com.ChattingSystem.media.entity.MediaStatus.COMPLETING")
    int updateStatusToBlocked(@Param("id") Long id,
                              @Param("reason") String reason,
                              @Param("now") Instant now);

    /**
     * Atomic claim hủy phiên: chuyển từ CREATING hoặc UPLOADING sang FAILED.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE MediaAsset a SET a.status = com.ChattingSystem.media.entity.MediaStatus.FAILED, " +
           "a.lastError = :reason, a.updatedAt = :now " +
           "WHERE a.id = :id AND a.uploaderId = :uploaderId " +
           "AND a.status IN (com.ChattingSystem.media.entity.MediaStatus.CREATING, com.ChattingSystem.media.entity.MediaStatus.UPLOADING)")
    int claimForAbort(@Param("id") Long id,
                      @Param("uploaderId") Long uploaderId,
                      @Param("reason") String reason,
                      @Param("now") Instant now);

    /**
     * Cập nhật uploadId và chuyển từ CREATING sang UPLOADING sau khi gọi storage tạo multipart thành công.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE MediaAsset a SET a.status = com.ChattingSystem.media.entity.MediaStatus.UPLOADING, " +
           "a.uploadId = :uploadId, a.updatedAt = :now " +
           "WHERE a.id = :id AND a.status = com.ChattingSystem.media.entity.MediaStatus.CREATING")
    int updateUploadIdAndMarkUploading(@Param("id") Long id,
                                       @Param("uploadId") String uploadId,
                                       @Param("now") Instant now);

    /**
     * Đánh dấu FAILED khi khởi tạo multipart upload thất bại trên dịch vụ lưu trữ.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE MediaAsset a SET a.status = com.ChattingSystem.media.entity.MediaStatus.FAILED, " +
           "a.lastError = :reason, a.updatedAt = :now " +
           "WHERE a.id = :id AND a.status = com.ChattingSystem.media.entity.MediaStatus.CREATING")
    int markCreatingFailed(@Param("id") Long id,
                           @Param("reason") String reason,
                           @Param("now") Instant now);

    /**
     * Cập nhật có điều kiện sang AVAILABLE khi xử lý hậu kỳ thành công.
     * Chỉ cập nhật nếu trạng thái hiện tại vẫn là PROCESSING.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE MediaAsset a SET a.status = com.ChattingSystem.media.entity.MediaStatus.AVAILABLE, " +
           "a.displayKey = :displayKey, a.thumbKey = :thumbKey, a.contentType = :contentType, " +
           "a.width = :width, a.height = :height, a.durationMs = :durationMs, a.lockedUntil = null, a.updatedAt = :now " +
           "WHERE a.id = :id AND a.status = com.ChattingSystem.media.entity.MediaStatus.PROCESSING")
    int markProcessingAvailable(@Param("id") Long id,
                                @Param("displayKey") String displayKey,
                                @Param("thumbKey") String thumbKey,
                                @Param("contentType") String contentType,
                                @Param("width") Integer width,
                                @Param("height") Integer height,
                                @Param("durationMs") Integer durationMs,
                                @Param("now") Instant now);

    /**
     * Cập nhật có điều kiện sang BLOCKED khi tệp vi phạm chính sách an toàn (bom nén, quá thời lượng, kích thước âm/vượt trần).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE MediaAsset a SET a.status = com.ChattingSystem.media.entity.MediaStatus.BLOCKED, " +
           "a.lastError = :reason, a.lockedUntil = null, a.updatedAt = :now " +
           "WHERE a.id = :id AND a.status = com.ChattingSystem.media.entity.MediaStatus.PROCESSING")
    int markProcessingBlocked(@Param("id") Long id,
                              @Param("reason") String reason,
                              @Param("now") Instant now);

    /**
     * Quét các job poison-pill (đã vượt quá số lần thử tối đa và không còn bị khóa).
     */
    @Query("SELECT a FROM MediaAsset a WHERE a.status = com.ChattingSystem.media.entity.MediaStatus.PROCESSING " +
           "AND a.attempts >= :maxAttempts AND (a.lockedUntil IS NULL OR a.lockedUntil < :now)")
    List<MediaAsset> findPoisonPillJobs(@Param("maxAttempts") int maxAttempts, @Param("now") Instant now);

    /**
     * Cập nhật có điều kiện sang FAILED khi vượt quá số lần thử tối đa (attempts >= maxAttempts).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE MediaAsset a SET a.status = com.ChattingSystem.media.entity.MediaStatus.FAILED, " +
           "a.lastError = :reason, a.lockedUntil = null, a.updatedAt = :now " +
           "WHERE a.id = :id AND a.status = com.ChattingSystem.media.entity.MediaStatus.PROCESSING")
    int markProcessingFailed(@Param("id") Long id,
                             @Param("reason") String reason,
                             @Param("now") Instant now);

    /**
     * Lập lịch thử lại (retry backoff): cập nhật next_run_at, giải phóng khóa locked_until.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE MediaAsset a SET a.nextRunAt = :nextRunAt, a.lastError = :lastError, " +
           "a.lockedUntil = null, a.updatedAt = :now " +
           "WHERE a.id = :id AND a.status = com.ChattingSystem.media.entity.MediaStatus.PROCESSING")
    int scheduleRetry(@Param("id") Long id,
                      @Param("nextRunAt") Instant nextRunAt,
                      @Param("lastError") String lastError,
                      @Param("now") Instant now);

    /**
     * Hoàn lại lượt claim khi TaskExecutor từ chối thực thi (thread pool quá tải):
     * Giảm số lần attempts, giải phóng locked_until để vòng lặp sau có thể claim lại.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE MediaAsset a SET a.attempts = CASE WHEN a.attempts > 0 THEN a.attempts - 1 ELSE 0 END, " +
           "a.lockedUntil = null, a.updatedAt = :now " +
           "WHERE a.id = :id AND a.status = com.ChattingSystem.media.entity.MediaStatus.PROCESSING")
    int releaseClaimOnReject(@Param("id") Long id, @Param("now") Instant now);

    /**
     * Cập nhật nguyên tử sang DELETED khi chủ sở hữu hoặc OWNER của phòng yêu cầu xóa tệp.
     * Chỉ xóa được nếu tệp ở trạng thái PROCESSING, AVAILABLE, FAILED hoặc BLOCKED.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE MediaAsset a SET a.status = com.ChattingSystem.media.entity.MediaStatus.DELETED, " +
           "a.deletedAt = :now, a.lockedUntil = null, a.updatedAt = :now " +
           "WHERE a.id = :id AND a.status IN (" +
           "com.ChattingSystem.media.entity.MediaStatus.PROCESSING, " +
           "com.ChattingSystem.media.entity.MediaStatus.AVAILABLE, " +
           "com.ChattingSystem.media.entity.MediaStatus.FAILED, " +
           "com.ChattingSystem.media.entity.MediaStatus.BLOCKED)")
    int markStatusToDeleted(@Param("id") Long id, @Param("now") Instant now);
}
