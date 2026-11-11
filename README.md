# ChatChit Backend (ChattingSystem)

Hệ thống API Backend cho **ChatChit** (https://chatchit.me) — nền tảng trò chuyện tức thì theo vị trí địa lý (Location-based Chat) dành cho thị trường Việt Nam.

- **Ngôn ngữ & Runtime:** Java 25
- **Framework:** Spring Boot 4.1.x (Spring Web MVC)
- **Database & Spatial:** PostgreSQL + PostGIS (truy vấn không gian địa lý `ST_DWithin`)
- **Database Migration:** Flyway (`src/main/resources/db/migration/`)
- **Cache & Realtime:** Redis (`RedisService` có fallback về DB), WebSocket / STOMP (`/api/v1/ws`)
- **Security & Cloud:** Spring Security (OAuth2 Resource Server, HS256 JWT), Firebase Admin SDK, AWS SDK v2 S3 Client (Cloudflare R2), Jsoup (HTML Sanitizer).

---

## 1. Cấu trúc thư mục (Package-by-Feature)

Source code backend được tổ chức theo từng module nghiệp vụ độc lập tại `src/main/java/com/ChattingSystem/`:

```text
src/main/java/com/ChattingSystem/
├── ChattingSystemApplication.java   # Điểm khởi chạy ứng dụng (Main Entry Point)
├── auth/                            # Xác thực Firebase Google -> cấp phát HS256 JWT
│   ├── controller/                  # POST /api/v1/auth/login, GET /api/v1/auth/me
│   ├── service/ & service/impl/
│   └── dto/ (request & response)
├── chatroom/                        # Quản lý phòng chat theo toạ độ PostGIS
│   ├── controller/                  # Quét phòng lân cận, tạo phòng, join phòng
│   ├── entity/                      # ChatRoom entity (PostGIS Point, radius, TTL)
│   ├── repository/                  # Truy vấn không gian ST_DWithin, ST_MakePoint
│   ├── service/ & service/impl/
│   ├── util/                        # LocationFuzzer, DistanceLabel (bảo mật vị trí)
│   └── dto/
├── message/                         # Tin nhắn phòng chat (REST fallback + STOMP)
│   ├── controller/, entity/, repository/, service/
├── realtime/                        # Cấu hình WebSocket & STOMP Protocol
│   ├── config/                      # WebSocketMessageBrokerConfig (/api/v1/ws)
│   ├── event/                       # RoomEventBroadcaster (phát sóng tin nhắn)
│   └── security/                    # STOMP Channel Interceptors xác thực JWT
├── post/                            # Bài viết blog chuẩn SEO, tag, slug, sanitize HTML
│   ├── controller/, entity/, repository/, service/, util/ (SlugUtils, ReadingTime)
├── storage/                         # Upload ảnh lên Cloudflare R2 (S3 Client)
├── user/                            # Quản lý người dùng, phân quyền (USER, ADMIN)
├── waitlist/                        # Đăng ký nhận thông báo sớm cho landing page
├── security/                        # Cấu hình Spring Security, JwtConfig, AuthEntryPoint
├── config/                          # CorsConfig, FirebaseConfig
└── shared/                          # Thành phần dùng chung toàn hệ thống
    ├── entity/BaseEntity.java       # ID, createdAt, updatedAt (JPA Auditing)
    ├── exeption/                    # AppException, ErrorCode, GlobalExceptionHandler
    ├── response/                    # ApiResponse, PageResponse
    └── redis/                       # RedisService (tự động fallback khi Redis offline)
```

---

## 2. Cơ chế cốt lõi (Core Mechanics)

### 2.1. Quyền riêng tư vị trí (Location Privacy & PostGIS)
- **Tọa độ thực tế (`center`)**: Tọa độ chính xác của phòng chat và người dùng **tuyệt đối không bao giờ được gửi về Client**.
- **Làm mờ vị trí (`displayCenter`)**: Hệ thống áp dụng thuật toán `LocationFuzzer` làm mờ vị trí hiển thị và chỉ trả về nhãn khoảng cách tương đối qua `DistanceLabel` (ví dụ: *"Dưới 100 m"*, *"Khoảng 300 m"*, *“Khoảng 1.2 km”*).
- **Phạm vi quét động**: Hỗ trợ dải bán kính từ **5 km đến 50 km** (`50000m`) phục vụ linh hoạt cho cả khu vực lân cận và quy mô toàn thành phố.

### 2.2. Luồng xác thực & Phân quyền (Authentication Flow)
1. **Client**: Người dùng đăng nhập bằng tài khoản Google thông qua Firebase Web SDK.
2. **Exchange**: Client gửi Firebase ID Token lên `POST /api/v1/auth/login`.
3. **Cấp phát JWT**: Backend dùng Firebase Admin SDK xác thực token, lưu thông tin User vào PostgreSQL, và ký cấp JWT nội bộ (HMAC-SHA256, thời hạn mặc định 15 phút).
4. **Phân quyền (RBAC)**:
   - Người dùng thông thường: Quyền `SCOPE_USER`.
   - Quản trị viên: Quyền `SCOPE_ADMIN` (các endpoint `/api/v1/admin/**` bắt buộc quyền này).
   - Để cấp quyền Admin cho một user:
     ```sql
     UPDATE users SET role = 'ADMIN' WHERE email = 'admin@example.com';
     ```
     *(Sau khi cập nhật role trong DB, người dùng cần đăng nhập lại để nhận JWT mới có claim `SCOPE_ADMIN`).*

### 2.3. Realtime Chat & Caching
- **Endpoint WebSocket**: Kết nối STOMP tại `${API_URL}/ws` (context path `/api/v1/ws`).
- **Topic đăng ký**: `/topic/rooms/{roomId}` nhận tin nhắn và sự kiện phòng tức thì.
- **Khả năng chịu lỗi (Fault-Tolerant Cache)**: `RedisService` bọc toàn bộ tương tác Redis. Nếu Redis gặp sự cố, hệ thống tự động fallback đọc/ghi trực tiếp từ PostgreSQL, đảm bảo ứng dụng không bao giờ bị dừng hoạt động.
- **An toàn nội dung**: Tin nhắn chat chỉ được render dạng **plain text**, ngăn chặn triệt để tấn công XSS.

---

## 3. Cài đặt & Chạy ứng dụng Local

### 3.1. Yêu cầu môi trường
- **Java 25** (Khuyên dùng Eclipse Temurin 25 hoặc OpenJDK 25).
- **Maven 3.9+** (hoặc dùng trực tiếp `./mvnw` đi kèm dự án).
- **Docker & Docker Compose** (để khởi chạy PostgreSQL có PostGIS và Redis local).

### 3.2. Khởi động môi trường Database & Cache Local
Để chạy local an toàn mà không ảnh hưởng tới DB cloud, khởi chạy container PostGIS và Redis:

```bash
# Khởi chạy PostGIS 17 + Redis 7
docker run -d --name chatchit-local-pg \
  -e POSTGRES_DB=chatchit \
  -e POSTGRES_USER=chatchit \
  -e POSTGRES_PASSWORD=chatchit \
  -p 5432:5432 postgis/postgis:17-3.5

docker run -d --name chatchit-local-redis \
  -p 6379:6379 redis:7
```

### 3.3. Thiết lập biến môi trường
Tạo file `.env` tại thư mục gốc backend (`ChattingSystem/.env`):

```bash
cp .env.example .env
```

Điền các thông tin kết nối DB, Redis và Firebase Service Account JSON (dạng raw chuỗi JSON một dòng).

### 3.4. Chạy ứng dụng

```bash
# 1. Chạy ứng dụng local (tự động nạp file .env)
./mvnw spring-boot:run

# 2. Build file JAR (bỏ qua test)
./mvnw clean package -DskipTests

# 3. Chạy file JAR đã build
java -jar target/ChattingSystem-0.0.1-SNAPSHOT.jar
```

Ứng dụng sẽ khởi chạy tại: `http://localhost:8080/api/v1`.

---

## 4. Hướng dẫn Kiểm thử (Testing)

> [!WARNING]
> **QUY TẮC AN TOÀN SỐNG CÒN**:
> Tuyệt đối **KHÔNG CHẠY TEST** trỏ vào Database Cloud/Production trong `.env` vì Flyway sẽ tự động chạy migration lên DB đó.

### 4.1. Chạy Unit Test (Không cần Database)
Các unit test độc lập không phụ thuộc DB có thể chạy ngay lập tức:

```bash
./mvnw test -Dtest='SlugUtilsTest,ReadingTimeCalculatorTest,HtmlSanitizerTest,ImageTypeTest'
```

### 4.2. Chạy Full Integration Test với Container cách ly

```bash
# 1. Tạo container test riêng biệt (port 55432 & 56379)
docker run -d --name chatchit-test-pg -e POSTGRES_DB=chatchit -e POSTGRES_USER=chatchit -e POSTGRES_PASSWORD=chatchit -p 55432:5432 postgis/postgis:17-3.5
docker run -d --name chatchit-test-redis -p 56379:6379 redis:7

# 2. Chạy test với biến môi trường ghi đè tạm thời
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/chatchit \
SPRING_DATASOURCE_USERNAME=chatchit \
SPRING_DATASOURCE_PASSWORD=chatchit \
SPRING_DATA_REDIS_URL=redis://localhost:56379 \
./mvnw test
```

---

## 5. Danh mục Biến môi trường (`.env`)

| Biến | Bắt buộc | Mặc định | Ý nghĩa & Ghi chú |
| :--- | :---: | :--- | :--- |
| `PORT` | Không | `8080` | Cổng HTTP Server (Railway tự động inject biến này). |
| `DB_HOST` | **Có** | `localhost` | Địa chỉ máy chủ PostgreSQL. |
| `DB_PORT` | **Có** | `5432` | Cổng kết nối PostgreSQL. |
| `DB_NAME` | **Có** | `chatchit` | Tên cơ sở dữ liệu. |
| `DB_USERNAME` | **Có** | — | Tên người dùng database. |
| `DB_PASSWORD` | **Có** | — | Mật khẩu database. |
| `REDIS_URL` | Không | `redis://localhost:6379` | URL kết nối Redis (ví dụ: `redis://default:pass@host:6379`). |
| `FIREBASE_SERVICE_ACCOUNT` | **Có** | — | **Toàn bộ nội dung chuỗi JSON** của Firebase Admin Service Account key. |
| `JWT_SECRET_KEY` | **Có** | — | Khóa bí mật ký HMAC-SHA256 (tối thiểu 32 ký tự / 256 bits). |
| `JWT_ACCESS_EXPIRATION` | Không | `15m` | Thời hạn sống của Access Token (`15m`, `1h`,...). |
| `JWT_REFRESH_EXPIRATION` | Không | `30d` | Thời hạn sống của Refresh Token. |
| `CORS_ALLOWED_ORIGINS` | Không | `http://localhost:3000,...` | Danh sách domain được phép gọi API (phân cách bằng dấu phẩy). |
| `STORAGE_ENDPOINT` | Không | — | Endpoint S3 Cloudflare R2 (để trống sẽ tắt tính năng upload). |
| `STORAGE_REGION` | Không | `auto` | Region lưu trữ R2 (mặc định `auto`). |
| `STORAGE_BUCKET` | Không | — | Tên bucket Cloudflare R2. |
| `STORAGE_ACCESS_KEY` | Không | — | R2 Access Key ID. |
| `STORAGE_SECRET_KEY` | Không | — | R2 Secret Access Key. |
| `STORAGE_PUBLIC_BASE_URL` | Không | — | Domain public của R2 (ví dụ: `https://pub-*.r2.dev` hoặc custom domain). |

---

## 6. Quy chuẩn Lập trình (Coding Conventions)

Khi phát triển thêm tính năng cho Backend, bắt buộc tuân thủ:

1. **Dependency Injection**: Luôn viết Constructor Injection tường minh. **Tuyệt đối không dùng** `@Autowired` trên field, **không dùng** `@RequiredArgsConstructor` của Lombok.
2. **Service Layer**: Luôn tách interface trong `service/` và class thực thi tương ứng trong `service/impl/`.
3. **Data Transfer Objects (DTO)**: Sử dụng Java `record`, đặt trong `dto/request/` và `dto/response/`.
4. **JPA Entity**:
   - Mọi entity kế thừa từ `BaseEntity` (tự động sinh `id`, `createdAt`, `updatedAt`).
   - Constructor rỗng mang mức truy cập `protected`.
   - Cung cấp static factory method `create(...)`.
   - **Tuyệt đối không dùng public setters**, thay thế bằng các intent-named mutators (ví dụ: `updateProfile(...)`, `deactivate()`).
5. **Database Migration (Flyway)**:
   - Ứng dụng cấu hình `ddl-auto: validate`. Mọi thay đổi schema bắt buộc phải viết migration script mới: `src/main/resources/db/migration/V{n}__*.sql`.
   - **Tuyệt đối không sửa file migration đã áp dụng**.
6. **Exception & ApiResponse Envelope**:
   - Ném ngoại lệ nghiệp vụ tập trung bằng `AppException(ErrorCode.XYZ)` hoặc `AppException.fieldError(field, message)`.
   - Toàn bộ response trả về client bọc qua `ApiResponse.success(message, data)` hoặc `PageResponse.of(page)`.
   - 100% thông điệp người dùng và mã lỗi viết bằng **Tiếng Việt**.

---

## 7. Triển khai & Đóng gói Docker

Dự án cung cấp sẵn `Dockerfile` multi-stage build tối ưu kích thước:

```bash
# Build production image
docker build -t chattingsystem:latest .

# Chạy container
docker run -d -p 8080:8080 --env-file .env chattingsystem:latest
```

Hệ thống được thiết kế sẵn sàng triển khai trên **Railway** (cấu hình qua `railway.toml`, tự động kiểm tra health check tại `GET /api/v1/tags`), hoặc trên **Azure Container Apps / Virtual Machine**.
