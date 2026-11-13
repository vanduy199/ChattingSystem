# AGENT.md - Chatchit / ChattingSystem Backend

Tài liệu hướng dẫn tổng quan về kiến trúc, quy chuẩn và quy trình vận hành dự án dành cho AI Agent & Developer để nắm bắt nhanh ngữ cảnh mà không cần đọc lại toàn bộ mã nguồn.

---

## 1. Tổng quan dự án (Overview)

- **Tên dự án:** ChattingSystem (Chatchit)
- **Tech Stack:**
  - **Ngôn ngữ:** Java 25
  - **Framework:** Spring Boot 4.1.x (Spring Web MVC)
  - **Build tool:** Maven Wrapper (`./mvnw`)
  - **Database:** PostgreSQL + PostGIS (truy vấn vị trí địa lý)
  - **Migration:** Flyway (`src/main/resources/db/migration/`)
  - **ORM:** Spring Data JPA + Hibernate (`ddl-auto: validate`, `open-in-view: false`)
  - **Cache:** Redis (`RedisService` có fallback về DB khi Redis offline)
  - **Security:** Spring Security (OAuth2 Resource Server, Bearer JWT tự cấp bằng HS256)
  - **Third-party:** Firebase Admin SDK (xác thực token đăng nhập), AWS SDK v2 S3 (lưu file lên Cloudflare R2), Jsoup (sanitizer HTML).
  - **Realtime:** STOMP over plain WebSocket (`/api/v1/ws`).

---

## 2. Cấu trúc thư mục & Kiến trúc (Package-by-Feature)

Source code nằm tại `src/main/java/com/ChattingSystem/`, được tổ chức theo từng module tính năng (**Package-by-Feature**):

```text
com.ChattingSystem/
├── ChattingSystemApplication.java    # Main Entry Point
├── auth/                             # Xác thực Firebase & Guest -> sinh JWT app
│   ├── controller/
│   ├── service/ & service/impl/
│   └── dto/ (request & response)
├── chatroom/                         # Phòng chat theo tọa độ PostGIS
│   ├── controller/
│   ├── service/ & service/impl/
│   ├── repository/
│   ├── entity/
│   ├── util/ (LocationFuzzer, DistanceLabel)
│   └── dto/
├── message/                          # Tin nhắn phòng chat (REST fallback + STOMP)
├── realtime/                         # WebSocket, STOMP configuration & interceptors
├── post/                             # Bài viết blog, tag, sanitize HTML (jsoup), slug
├── storage/                          # Upload ảnh lên Cloudflare R2 (S3 client)
├── waitlist/                         # Danh sách chờ / đăng ký sớm
├── security/                         # Cấu hình Spring Security, JwtConfig, Handlers
├── config/                           # CORS, FirebaseConfig
└── shared/                           # Thành phần dùng chung toàn hệ thống
    ├── entity/BaseEntity.java        # ID, createdAt, updatedAt (Audited)
    ├── exeption/                     # AppException, ErrorCode, GlobalExceptionHandler (lưu ý chính tả: shared.exeption)
    ├── response/                     # ApiResponse, PageResponse
    ├── redis/                        # RedisService
    └── util/                         # SlugUtils, HashToken
```

---

## 3. Luồng xác thực & Phân quyền (Authentication & Authorization)

### 3.1. Người dùng có tài khoản (Registered User)
- Đăng nhập Firebase ở Client $\rightarrow$ Gửi ID token lên `POST /api/v1/auth/login`.
- `AuthServiceImpl` xác thực qua Firebase Admin SDK $\rightarrow$ Sinh JWT có claim `userId`, `scope = "USER"` (hoặc `"USER ADMIN"`).

### 3.2. Khách vãng lai / Chưa có tài khoản (Guest User - V9)
- Client gọi `POST /api/v1/auth/guest` (có thể kèm `{ nickname }` hoặc hệ thống tự sinh ngẫu nhiên `"Khách #XXXX"`).
- Tạo bản ghi trong `users` với `role = Role.GUEST` (cột `firebase_uid` và `email` cho phép `NULL` từ migration `V9`).
- `JwtService` cấp JWT có `scope = "GUEST"` và `subject = "guest:<id>"`.
- **Quy tắc phân quyền Guest:**
  - **Phòng PUBLIC:** Guest được phép tham gia (`joinRoom` $\rightarrow$ `ACTIVE`) và chat (qua WebSocket STOMP hoặc REST).
  - **Phòng PRIVATE:** 
    - Guest bị chặn khi xin tham gia $\rightarrow$ ném lỗi 403 `LOGIN_REQUIRED_FOR_PRIVATE_ROOM`.
    - Guest bị chặn khi gửi tin nhắn $\rightarrow$ ném lỗi 403 `LOGIN_REQUIRED_TO_CHAT`.
  - **Tạo phòng:** Guest bị chặn khi tạo phòng $\rightarrow$ ném lỗi 403 `LOGIN_REQUIRED_TO_CREATE_ROOM`.

### 3.3. Giai đoạn 5: Liên kết / Nâng cấp tài khoản Khách (Account Linking & Upgrade)
Khi người dùng đang ở chế độ Khách (gửi header `Authorization: Bearer <guest_token>` hoặc truyền `{ idToken, guestToken }` trong body) bấm đăng nhập Firebase (`POST /api/v1/auth/login`):
- **Trường hợp 1 (Tài khoản Firebase mới):**
  - Nâng cấp trực tiếp bản ghi User Khách hiện tại: gán `firebase_uid`, `email`, `fullName` (theo Firebase), đổi `role = Role.USER`.
  - Giữ nguyên `userId`, bảo toàn toàn bộ lịch sử tin nhắn và các phòng chat đã tham gia.
- **Trường hợp 2 (Tài khoản Firebase đã có sẵn):**
  - Xóa các bản ghi `room_members` của Khách tại các phòng mà tài khoản thật đã tham gia (tránh vi phạm unique `(room_id, user_id)`).
  - Di chuyển các `room_members` còn lại của Khách sang tài khoản thật (`userId = targetUserId`).
  - Di chuyển toàn bộ tin nhắn (`messages.sender_id`) sang tài khoản thật.
  - Chuyển phòng đã tạo (`chat_rooms.created_by`) nếu có.
  - Xóa tài khoản Khách tạm thời và cấp JWT cho tài khoản thật.

---

## 4. Quy chuẩn code (Conventions & Best Practices)

- **Dependency Injection:** Viết tường minh bằng Constructor Injection (không dùng `@Autowired` lên field, không dùng `@RequiredArgsConstructor` của Lombok).
- **Service Pattern:** Luôn tách Interface trong `service/` và implementation trong `service/impl/`.
- **DTOs:** Sử dụng Java `record`, đặt trong `dto/request/` và `dto/response/`.
- **Validation:** Sử dụng Jakarta Validation (`@NotBlank`, `@NotNull`, `@Valid`,...).
- **API Response:** Mọi response thành công bọc trong `ApiResponse.success(message, data)`. Phân trang dùng `PageResponse.of(page)`.
- **Exception:** Ném lỗi nghiệp vụ bằng `AppException(ErrorCode.XYZ)` hoặc `AppException.fieldError(fieldName, message)`.
- **Flyway:** `ddl-auto: validate`. Mọi thay đổi Entity phải có file SQL migration tương ứng (`V{n}__*.sql`). Tuyệt đối không sửa file cũ.
- **Tọa độ thực (center):** Tọa độ chính xác của phòng chat **không bao giờ được gửi về Client**. Chỉ trả về khoảng cách tương đối qua `DistanceLabel`.

---

## 5. Các lệnh phát triển (Commands)

```bash
# 1. Chạy ứng dụng local (tự động load .env)
./mvnw spring-boot:run

# 2. Build ứng dụng
./mvnw clean package -DskipTests

# 3. Chạy unit tests độc lập (không phụ thuộc database)
./mvnw test -Dtest='SlugUtilsTest,ReadingTimeCalculatorTest,HtmlSanitizerTest,ImageTypeTest,ChatRoomServiceUnitTest,MessageServiceUnitTest,AuthServiceUnitTest'

# 4. Build Docker Image
docker build -t chattingsystem .
```
