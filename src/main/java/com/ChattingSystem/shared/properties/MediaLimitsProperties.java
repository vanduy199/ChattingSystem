package com.ChattingSystem.shared.properties;

import com.ChattingSystem.media.entity.MediaKind;
import com.ChattingSystem.user.entity.Role;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình giới hạn dung lượng và số lượng tệp theo vai trò người dùng và loại phòng chat.
 */
@ConfigurationProperties(prefix = "media.limits")
public record MediaLimitsProperties(
        RoleLimits imageMaxBytes,
        RoleLimits videoMaxBytes,
        RoleLimitsInt maxFilesPerMessage,
        RoleLimitsInt maxOpenUploads,
        RoleLimitsInt maxInitPerMinute
) {

    public MediaLimitsProperties {
        imageMaxBytes = (imageMaxBytes != null) ? imageMaxBytes : new RoleLimits(5242880L, 20971520L, 26214400L);
        videoMaxBytes = (videoMaxBytes != null) ? videoMaxBytes : new RoleLimits(20971520L, 52428800L, 104857600L);
        maxFilesPerMessage = (maxFilesPerMessage != null) ? maxFilesPerMessage : new RoleLimitsInt(2, 5, 10);
        maxOpenUploads = (maxOpenUploads != null) ? maxOpenUploads : new RoleLimitsInt(3, 10, 10);
        maxInitPerMinute = (maxInitPerMinute != null) ? maxInitPerMinute : new RoleLimitsInt(10, 30, 30);
    }

    public record RoleLimits(long guest, long publicRoom, long privateRoom) {
        public long getLimit(Role role, String roomType) {
            if (role == Role.GUEST) {
                return guest;
            }
            if ("private".equalsIgnoreCase(roomType)) {
                return privateRoom;
            }
            return publicRoom;
        }
    }

    public record RoleLimitsInt(int guest, int publicRoom, int privateRoom) {
        public int getLimit(Role role, String roomType) {
            if (role == Role.GUEST) {
                return guest;
            }
            if ("private".equalsIgnoreCase(roomType)) {
                return privateRoom;
            }
            return publicRoom;
        }
    }

    /**
     * Lấy dung lượng tối đa (bytes) cho phép tải lên theo loại phương tiện, vai trò và loại phòng.
     */
    public long getMaxSizeBytes(MediaKind kind, Role role, String roomType) {
        if (kind == MediaKind.IMAGE) {
            return imageMaxBytes.getLimit(role, roomType);
        }
        return videoMaxBytes.getLimit(role, roomType);
    }
}
