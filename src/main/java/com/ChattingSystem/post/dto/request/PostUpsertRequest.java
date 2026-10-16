package com.ChattingSystem.post.dto.request;

import com.ChattingSystem.post.entity.PostStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PostUpsertRequest(
        @NotBlank(message = "Tiêu đề không được để trống")
        @Size(max = 200, message = "Tiêu đề tối đa 200 ký tự")
        String title,

        @Size(max = 200, message = "Slug tối đa 200 ký tự")
        @Pattern(
                regexp = "^\\s*$|^[a-z0-9]+(-[a-z0-9]+)*$",
                message = "Slug chỉ gồm chữ thường không dấu, số và dấu gạch ngang"
        )
        String slug,

        @NotBlank(message = "Mô tả ngắn không được để trống")
        @Size(max = 500, message = "Mô tả ngắn tối đa 500 ký tự")
        String excerpt,

        @NotBlank(message = "Nội dung không được để trống")
        @Size(max = 200000, message = "Nội dung tối đa 200000 ký tự")
        String contentHtml,

        @Size(max = 1000, message = "URL ảnh bìa tối đa 1000 ký tự")
        String coverImageUrl,

        @Size(max = 70, message = "Meta title tối đa 70 ký tự")
        String metaTitle,

        @Size(max = 170, message = "Meta description tối đa 170 ký tự")
        String metaDescription,

        @Size(max = 10, message = "Tối đa 10 thẻ")
        List<
                @NotBlank(message = "Tên thẻ không được để trống")
                @Size(max = 50, message = "Tên thẻ tối đa 50 ký tự")
                String> tags,

        @NotNull(message = "Trạng thái không được để trống")
        PostStatus status
) {
}
