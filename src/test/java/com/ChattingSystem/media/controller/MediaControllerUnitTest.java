package com.ChattingSystem.media.controller;

import com.ChattingSystem.media.dto.request.BatchAccessUrlRequest;
import com.ChattingSystem.media.dto.request.CompletedPartDto;
import com.ChattingSystem.media.dto.request.CompleteUploadRequest;
import com.ChattingSystem.media.dto.request.InitUploadRequest;
import com.ChattingSystem.media.dto.response.BatchAccessUrlResponse;
import com.ChattingSystem.media.dto.response.CompleteUploadResponse;
import com.ChattingSystem.media.dto.response.InitUploadResponse;
import com.ChattingSystem.media.dto.response.MediaAccessItemResponse;
import com.ChattingSystem.media.dto.response.MediaUrlsDto;
import com.ChattingSystem.media.dto.response.UploadStatusResponse;
import com.ChattingSystem.media.dto.response.UploadTargetDto;
import com.ChattingSystem.media.entity.MediaKind;
import com.ChattingSystem.media.entity.MediaStatus;
import com.ChattingSystem.media.service.MediaAccessService;
import com.ChattingSystem.media.service.MediaUploadService;
import com.ChattingSystem.shared.exeption.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MediaController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("Kiểm thử MediaController (Tầng Web REST API)")
class MediaControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private MediaUploadService mediaUploadService;

    @MockitoBean
    private MediaAccessService mediaAccessService;

    @MockitoBean
    private org.springframework.data.jpa.mapping.JpaMetamodelMappingContext jpaMappingContext;

    private final Long userId = 100L;

    @Nested
    @DisplayName("1. POST /media/uploads (Khởi tạo phiên tải lên)")
    class InitUploadEndpoints {

        @Test
        @DisplayName("POST /media/uploads thành công -> trả về 200 OK và InitUploadResponse")
        void initUpload_success() throws Exception {
            UUID clientUploadId = UUID.randomUUID();
            InitUploadRequest request = new InitUploadRequest(
                    clientUploadId,
                    10L,
                    "image/jpeg",
                    2048L,
                    false
            );

            InitUploadResponse mockResponse = new InitUploadResponse(
                    301L,
                    MediaStatus.UPLOADING,
                    MediaKind.IMAGE,
                    "SINGLE",
                    5242880L,
                    1,
                    Instant.now().plusSeconds(900),
                    900L,
                    List.of(new UploadTargetDto(1, "PUT", "http://s3.local/put-url", java.util.Map.of("Content-Type", "image/jpeg")))
            );

            when(mediaUploadService.initUpload(eq(userId), any(InitUploadRequest.class)))
                    .thenReturn(mockResponse);

            mockMvc.perform(post("/media/uploads")
                            .with(jwt().jwt(builder -> builder.claim("userId", userId)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusCode").value(200))
                    .andExpect(jsonPath("$.message").value("Khởi tạo tải lên thành công"))
                    .andExpect(jsonPath("$.data.mediaAssetId").value(301))
                    .andExpect(jsonPath("$.data.status").value("UPLOADING"))
                    .andExpect(jsonPath("$.data.uploadMode").value("SINGLE"));

            verify(mediaUploadService).initUpload(eq(userId), any(InitUploadRequest.class));
        }

        @Test
        @DisplayName("POST /media/uploads thiếu clientUploadId -> trả về 400 Bad Request")
        void initUpload_missingClientUploadId_returns400() throws Exception {
            String jsonBody = """
                    {
                        "roomId": 10,
                        "contentType": "image/jpeg",
                        "sizeBytes": 2048,
                        "hd": false
                    }
                    """;

            mockMvc.perform(post("/media/uploads")
                            .with(jwt().jwt(builder -> builder.claim("userId", userId)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));

            verify(mediaUploadService, never()).initUpload(any(), any());
        }

        @Test
        @DisplayName("POST /media/uploads thiếu roomId -> trả về 400 Bad Request")
        void initUpload_missingRoomId_returns400() throws Exception {
            String jsonBody = String.format("""
                    {
                        "clientUploadId": "%s",
                        "contentType": "image/jpeg",
                        "sizeBytes": 2048,
                        "hd": false
                    }
                    """, UUID.randomUUID());

            mockMvc.perform(post("/media/uploads")
                            .with(jwt().jwt(builder -> builder.claim("userId", userId)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));

            verify(mediaUploadService, never()).initUpload(any(), any());
        }

        @Test
        @DisplayName("POST /media/uploads contentType rỗng -> trả về 400 Bad Request")
        void initUpload_blankContentType_returns400() throws Exception {
            InitUploadRequest request = new InitUploadRequest(
                    UUID.randomUUID(),
                    10L,
                    "   ",
                    2048L,
                    false
            );

            mockMvc.perform(post("/media/uploads")
                            .with(jwt().jwt(builder -> builder.claim("userId", userId)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));

            verify(mediaUploadService, never()).initUpload(any(), any());
        }

        @Test
        @DisplayName("POST /media/uploads sizeBytes <= 0 -> trả về 400 Bad Request")
        void initUpload_nonPositiveSizeBytes_returns400() throws Exception {
            InitUploadRequest request = new InitUploadRequest(
                    UUID.randomUUID(),
                    10L,
                    "image/jpeg",
                    0L,
                    false
            );

            mockMvc.perform(post("/media/uploads")
                            .with(jwt().jwt(builder -> builder.claim("userId", userId)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));

            verify(mediaUploadService, never()).initUpload(any(), any());
        }
    }

    @Nested
    @DisplayName("2. GET /media/uploads/{mediaAssetId} (Tra cứu trạng thái tải lên)")
    class GetUploadStatusEndpoints {

        @Test
        @DisplayName("GET /media/uploads/{id} thành công -> trả về 200 OK và UploadStatusResponse")
        void getUploadStatus_success() throws Exception {
            Long mediaAssetId = 302L;
            UploadStatusResponse mockResponse = new UploadStatusResponse(
                    mediaAssetId,
                    MediaStatus.UPLOADING,
                    "MULTIPART",
                    3,
                    Instant.now().plusSeconds(900),
                    900L,
                    List.of(),
                    List.of(new UploadTargetDto(1, "PUT", "http://s3.local/part1", java.util.Map.of()))
            );

            when(mediaUploadService.getUploadStatus(userId, mediaAssetId))
                    .thenReturn(mockResponse);

            mockMvc.perform(get("/media/uploads/{mediaAssetId}", mediaAssetId)
                            .with(jwt().jwt(builder -> builder.claim("userId", userId))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusCode").value(200))
                    .andExpect(jsonPath("$.message").value("Lấy trạng thái tải lên thành công"))
                    .andExpect(jsonPath("$.data.mediaAssetId").value(302))
                    .andExpect(jsonPath("$.data.status").value("UPLOADING"))
                    .andExpect(jsonPath("$.data.totalParts").value(3));

            verify(mediaUploadService).getUploadStatus(userId, mediaAssetId);
        }

        @Test
        @DisplayName("Claim userId dạng String trong JWT token -> trích xuất đúng Long userId")
        void getUploadStatus_stringUserIdClaim_success() throws Exception {
            Long mediaAssetId = 302L;
            UploadStatusResponse mockResponse = new UploadStatusResponse(
                    mediaAssetId,
                    MediaStatus.UPLOADING,
                    "MULTIPART",
                    3,
                    Instant.now().plusSeconds(900),
                    900L,
                    List.of(),
                    List.of()
            );

            when(mediaUploadService.getUploadStatus(userId, mediaAssetId))
                    .thenReturn(mockResponse);

            mockMvc.perform(get("/media/uploads/{mediaAssetId}", mediaAssetId)
                            .with(jwt().jwt(builder -> builder.claim("userId", "100"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.mediaAssetId").value(302));

            verify(mediaUploadService).getUploadStatus(100L, mediaAssetId);
        }
    }

    @Nested
    @DisplayName("3. POST /media/uploads/{mediaAssetId}/complete (Hoàn tất ghép mảnh)")
    class CompleteUploadEndpoints {

        @Test
        @DisplayName("POST /media/uploads/{id}/complete thành công -> trả về 200 OK và CompleteUploadResponse")
        void completeUpload_success() throws Exception {
            Long mediaAssetId = 303L;
            CompleteUploadRequest request = new CompleteUploadRequest(List.of(
                    new CompletedPartDto(1, "\"etag1\""),
                    new CompletedPartDto(2, "\"etag2\"")
            ));

            CompleteUploadResponse mockResponse = new CompleteUploadResponse(
                    mediaAssetId,
                    MediaStatus.PROCESSING,
                    MediaKind.VIDEO
            );

            when(mediaUploadService.completeUpload(eq(userId), eq(mediaAssetId), any(CompleteUploadRequest.class)))
                    .thenReturn(mockResponse);

            mockMvc.perform(post("/media/uploads/{mediaAssetId}/complete", mediaAssetId)
                            .with(jwt().jwt(builder -> builder.claim("userId", userId)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusCode").value(200))
                    .andExpect(jsonPath("$.message").value("Hoàn tất tải lên thành công"))
                    .andExpect(jsonPath("$.data.mediaAssetId").value(303))
                    .andExpect(jsonPath("$.data.status").value("PROCESSING"));

            verify(mediaUploadService).completeUpload(eq(userId), eq(mediaAssetId), any(CompleteUploadRequest.class));
        }

        @Test
        @DisplayName("POST /media/uploads/{id}/complete partNumber <= 0 -> trả về 400 Bad Request")
        void completeUpload_invalidPartNumber_returns400() throws Exception {
            Long mediaAssetId = 303L;
            CompleteUploadRequest request = new CompleteUploadRequest(List.of(
                    new CompletedPartDto(0, "\"etag0\"")
            ));

            mockMvc.perform(post("/media/uploads/{mediaAssetId}/complete", mediaAssetId)
                            .with(jwt().jwt(builder -> builder.claim("userId", userId)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));

            verify(mediaUploadService, never()).completeUpload(any(), any(), any());
        }

        @Test
        @DisplayName("POST /media/uploads/{id}/complete etag rỗng -> trả về 400 Bad Request")
        void completeUpload_blankEtag_returns400() throws Exception {
            Long mediaAssetId = 303L;
            CompleteUploadRequest request = new CompleteUploadRequest(List.of(
                    new CompletedPartDto(1, "   ")
            ));

            mockMvc.perform(post("/media/uploads/{mediaAssetId}/complete", mediaAssetId)
                            .with(jwt().jwt(builder -> builder.claim("userId", userId)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));

            verify(mediaUploadService, never()).completeUpload(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("4. DELETE /media/uploads/{mediaAssetId} (Hủy phiên tải lên)")
    class AbortUploadEndpoints {

        @Test
        @DisplayName("DELETE /media/uploads/{id} thành công -> trả về 200 OK và message hủy")
        void abortUpload_success() throws Exception {
            Long mediaAssetId = 304L;
            doNothing().when(mediaUploadService).abortUpload(userId, mediaAssetId);

            mockMvc.perform(delete("/media/uploads/{mediaAssetId}", mediaAssetId)
                            .with(jwt().jwt(builder -> builder.claim("userId", userId)))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusCode").value(200))
                    .andExpect(jsonPath("$.message").value("Hủy phiên tải lên thành công"));

            verify(mediaUploadService).abortUpload(userId, mediaAssetId);
        }
    }

    @Nested
    @DisplayName("5. POST /media/access-urls (Cấp Presigned GET URLs xem tệp theo lô)")
    class AccessUrlsEndpoints {

        @Test
        @DisplayName("POST /media/access-urls thành công -> trả về 200 OK và BatchAccessUrlResponse")
        void getAccessUrls_success() throws Exception {
            BatchAccessUrlRequest request = new BatchAccessUrlRequest(10L, List.of(301L, 302L));

            BatchAccessUrlResponse mockResponse = new BatchAccessUrlResponse(List.of(
                    MediaAccessItemResponse.available(
                            301L,
                            MediaKind.IMAGE,
                            "image/webp",
                            1920,
                            1080,
                            null,
                            1800L,
                            new MediaUrlsDto("http://s3.local/thumb.webp", "http://s3.local/display.webp")
                    ),
                    MediaAccessItemResponse.processing(
                            302L,
                            MediaKind.VIDEO,
                            "video/mp4",
                            1920,
                            1080,
                            15000
                    )
            ));

            when(mediaAccessService.getAccessUrls(eq(userId), any(BatchAccessUrlRequest.class)))
                    .thenReturn(mockResponse);

            mockMvc.perform(post("/media/access-urls")
                            .with(jwt().jwt(builder -> builder.claim("userId", userId)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusCode").value(200))
                    .andExpect(jsonPath("$.message").value("Cấp liên kết truy cập thành công"))
                    .andExpect(jsonPath("$.data.items").isArray())
                    .andExpect(jsonPath("$.data.items[0].mediaAssetId").value(301))
                    .andExpect(jsonPath("$.data.items[0].status").value("AVAILABLE"))
                    .andExpect(jsonPath("$.data.items[0].urls.display").value("http://s3.local/display.webp"))
                    .andExpect(jsonPath("$.data.items[1].mediaAssetId").value(302))
                    .andExpect(jsonPath("$.data.items[1].status").value("PROCESSING"));

            verify(mediaAccessService).getAccessUrls(eq(userId), any(BatchAccessUrlRequest.class));
        }

        @Test
        @DisplayName("POST /media/access-urls thiếu roomId -> trả về 400 Bad Request")
        void getAccessUrls_missingRoomId_returns400() throws Exception {
            String jsonBody = """
                    {
                        "mediaAssetIds": [301, 302]
                    }
                    """;

            mockMvc.perform(post("/media/access-urls")
                            .with(jwt().jwt(builder -> builder.claim("userId", userId)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));

            verify(mediaAccessService, never()).getAccessUrls(any(), any());
        }

        @Test
        @DisplayName("POST /media/access-urls mediaAssetIds rỗng -> trả về 400 Bad Request")
        void getAccessUrls_emptyMediaAssetIds_returns400() throws Exception {
            BatchAccessUrlRequest request = new BatchAccessUrlRequest(10L, List.of());

            mockMvc.perform(post("/media/access-urls")
                            .with(jwt().jwt(builder -> builder.claim("userId", userId)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));

            verify(mediaAccessService, never()).getAccessUrls(any(), any());
        }

        @Test
        @DisplayName("POST /media/access-urls mediaAssetIds vượt quá 50 phần tử -> trả về 400 Bad Request")
        void getAccessUrls_tooManyMediaAssetIds_returns400() throws Exception {
            List<Long> tooManyIds = new ArrayList<>();
            for (long i = 1; i <= 51; i++) {
                tooManyIds.add(i);
            }
            BatchAccessUrlRequest request = new BatchAccessUrlRequest(10L, tooManyIds);

            mockMvc.perform(post("/media/access-urls")
                            .with(jwt().jwt(builder -> builder.claim("userId", userId)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));

            verify(mediaAccessService, never()).getAccessUrls(any(), any());
        }
    }

    @Nested
    @DisplayName("6. DELETE /media/{mediaAssetId} (Xóa tệp phương tiện)")
    class DeleteAssetEndpoints {

        @Test
        @DisplayName("DELETE /media/{mediaAssetId} thành công -> trả về 200 OK")
        void deleteAsset_success() throws Exception {
            Long mediaAssetId = 301L;
            doNothing().when(mediaUploadService).deleteAsset(eq(userId), eq(mediaAssetId));

            mockMvc.perform(delete("/media/{mediaAssetId}", mediaAssetId)
                            .with(jwt().jwt(builder -> builder.claim("userId", userId)))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusCode").value(200))
                    .andExpect(jsonPath("$.message").value("Xóa tệp phương tiện thành công"));

            verify(mediaUploadService).deleteAsset(eq(userId), eq(mediaAssetId));
        }

        @Test
        @DisplayName("DELETE /media/{mediaAssetId} tệp không tồn tại hoặc không có quyền -> 404 NOT_FOUND")
        void deleteAsset_notFound() throws Exception {
            Long mediaAssetId = 999L;
            org.mockito.Mockito.doThrow(new com.ChattingSystem.shared.exeption.AppException(com.ChattingSystem.shared.exeption.ErrorCode.MEDIA_NOT_FOUND))
                    .when(mediaUploadService).deleteAsset(eq(userId), eq(mediaAssetId));

            mockMvc.perform(delete("/media/{mediaAssetId}", mediaAssetId)
                            .with(jwt().jwt(builder -> builder.claim("userId", userId)))
                            .with(csrf()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.statusCode").value(404));

            verify(mediaUploadService).deleteAsset(eq(userId), eq(mediaAssetId));
        }

        @Test
        @DisplayName("DELETE /media/{mediaAssetId} thành viên thường không có quyền xóa tệp người khác -> 403 FORBIDDEN")
        void deleteAsset_accessDenied() throws Exception {
            Long mediaAssetId = 301L;
            org.mockito.Mockito.doThrow(new com.ChattingSystem.shared.exeption.AppException(com.ChattingSystem.shared.exeption.ErrorCode.ACCESS_DENIED))
                    .when(mediaUploadService).deleteAsset(eq(userId), eq(mediaAssetId));

            mockMvc.perform(delete("/media/{mediaAssetId}", mediaAssetId)
                            .with(jwt().jwt(builder -> builder.claim("userId", userId)))
                            .with(csrf()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.statusCode").value(403));

            verify(mediaUploadService).deleteAsset(eq(userId), eq(mediaAssetId));
        }

        @Test
        @DisplayName("DELETE /media/{mediaAssetId} tệp đang xử lý -> 409 CONFLICT")
        void deleteAsset_uploadInProgress() throws Exception {
            Long mediaAssetId = 301L;
            org.mockito.Mockito.doThrow(new com.ChattingSystem.shared.exeption.AppException(com.ChattingSystem.shared.exeption.ErrorCode.UPLOAD_IN_PROGRESS))
                    .when(mediaUploadService).deleteAsset(eq(userId), eq(mediaAssetId));

            mockMvc.perform(delete("/media/{mediaAssetId}", mediaAssetId)
                            .with(jwt().jwt(builder -> builder.claim("userId", userId)))
                            .with(csrf()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.statusCode").value(409));

            verify(mediaUploadService).deleteAsset(eq(userId), eq(mediaAssetId));
        }

        @Test
        @DisplayName("DELETE /media/{mediaAssetId} không có JWT -> 401 UNAUTHORIZED")
        void deleteAsset_unauthenticated() throws Exception {
            mockMvc.perform(delete("/media/301")
                            .with(csrf()))
                    .andExpect(status().isUnauthorized());

            verify(mediaUploadService, never()).deleteAsset(any(), any());
        }
    }
}
