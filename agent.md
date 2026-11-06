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
├── auth/                             # Xác thực Firebase -> sinh JWT app
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

## 3. Quy chuẩn code (Conventions & Best Practices)

Bắt buộc tuân thủ các quy tắc sau khi tạo hoặc sửa đổi mã nguồn:

### 3.1. Dependency Injection & Service Layer
- **Dependency Injection:** Viết tường minh bằng Constructor Injection (không dùng `@Autowired` lên field, không dùng `@RequiredArgsConstructor` của Lombok).
- **Service Pattern:** Luôn tách Interface trong `service/` và implementation trong `service/impl/`.

### 3.2. DTO & Validation
- **DTOs:** Sử dụng Java `record`, đặt trong `dto/request/` và `dto/response/`.
- **Validation:** Sử dụng Jakarta Validation (`@NotBlank`, `@NotNull`, `@Valid`,...).

### 3.3. Response & Exception Handling
- **API Response:** Mọi response thành công bọc trong `ApiResponse.success(message, data)`. Phân trang dùng `PageResponse.of(page)`.
- **Exception:** Ném lỗi nghiệp vụ bằng `AppException(ErrorCode.XYZ)` hoặc `AppException.fieldError(fieldName, message)`.
- Các mã lỗi định nghĩa tập trung trong enum `ErrorCode` (kèm HTTP Status và message tiếng Việt).

### 3.4. JPA Entity & Database
- Kế thừa từ `BaseEntity`.
- Không sử dụng public setters.
- Sử dụng constructor rỗng `protected`, factory method `static create(...)` và các hàm đổi trạng thái theo ngữ cảnh cụ thể (intent-named mutators).
- **Flyway:** Quản lý toàn bộ database schema (`src/main/resources/db/migration/V{n}__*.sql`).
  - `ddl-auto: validate`: Mọi thay đổi về Entity phải có file SQL migration tương ứng.
  - **Tuyệt đối không chỉnh sửa file migration đã áp dụng.**

### 3.5. Bảo mật & Tọa độ địa lý
- **Tọa độ thực (center):** Tọa độ chính xác của phòng chat **không bao giờ được gửi về Client**. Chỉ trả về khoảng cách tương đối qua `DistanceLabel` (ví dụ: "Dưới 100 m",...).
- **Context Path:** `/api/v1`. Các endpoint công khai (`permitAll`) phải khai báo trong `SecurityConfig`. Đường dẫn `/admin/**` yêu cầu role `SCOPE_ADMIN`.

---

## 4. Các lệnh phát triển (Commands)

```bash
# 1. Chạy ứng dụng local (tự động load .env)
./mvnw spring-boot:run

# 2. Build ứng dụng
./mvnw clean package -DskipTests

# 3. Chạy unit tests (không phụ thuộc database)
./mvnw test -Dtest='SlugUtilsTest,ReadingTimeCalculatorTest,HtmlSanitizerTest,ImageTypeTest'

# 4. Chạy toàn bộ test (CHÚ Ý: Dùng container PostGIS local, KHÔNG chạy test lên DB Production/Cloud trong .env)
docker run -d --name chatchit-test-pg -e POSTGRES_DB=chatchit -e POSTGRES_USER=chatchit -e POSTGRES_PASSWORD=chatchit -p 55432:5432 postgis/postgis:17-3.5
docker run -d --name chatchit-test-redis -p 56379:6379 redis:7
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/chatchit SPRING_DATASOURCE_USERNAME=chatchit SPRING_DATASOURCE_PASSWORD=chatchit SPRING_DATA_REDIS_URL=redis://localhost:56379 ./mvnw test

# 5. Build Docker Image
docker build -t chattingsystem .
```

---

## 5. Biến môi trường quan trọng (`.env`)

| Biến | Ý nghĩa | Ghi chú |
| :--- | :--- | :--- |
| `PORT` | Cổng HTTP Server | Mặc định `8080` |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | Kết nối PostgreSQL + PostGIS | Database cloud/Supabase hoặc local container |
| `REDIS_URL` | Địa chỉ Redis | Mặc định `redis://localhost:6379` |
| `FIREBASE_SERVICE_ACCOUNT` | Nội dung JSON raw của Firebase Service Account | Cung cấp dưới dạng chuỗi JSON thô |
| `JWT_SECRET_KEY` | Khóa bí mật HMAC-SHA256 | Tối thiểu 32 bytes |
| `JWT_ACCESS_EXPIRATION`, `JWT_REFRESH_EXPIRATION` | Thời hạn JWT | Ví dụ: `15m`, `30d` |
| `CORS_ALLOWED_ORIGINS` | Danh sách domain CORS cho phép | Phân tách bởi dấu phẩy |
| `STORAGE_*` | Cấu hình Cloudflare R2 / S3 | Endpoint, bucket, access key, secret key |
