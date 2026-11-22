# REPO_FACTS.md

Tài liệu tổng hợp sự thật (facts) từ mã nguồn repository ChattingSystem (chỉ đọc, có bằng chứng file và dòng cụ thể, che thông tin nhạy cảm).

---

## A1. Tổng quan

- Phiên bản Java: **Java 25** (`pom.xml:18`)
- Phiên bản Spring Boot: **4.1.1** (`pom.xml:8`)
- Phiên bản Maven Wrapper: Maven **3.9.16**, wrapperVersion **3.3.4** (`.mvn/wrapper/maven-wrapper.properties:1-3`)
- Danh sách module / package (độ sâu tối đa 5 thư mục dưới `src/main/java`):
  ```text
  src/main/java/com
  src/main/java/com/ChattingSystem
  src/main/java/com/ChattingSystem/auth
  src/main/java/com/ChattingSystem/auth/controller
  src/main/java/com/ChattingSystem/auth/dto
  src/main/java/com/ChattingSystem/auth/dto/request
  src/main/java/com/ChattingSystem/auth/dto/response
  src/main/java/com/ChattingSystem/auth/service
  src/main/java/com/ChattingSystem/auth/service/impl
  src/main/java/com/ChattingSystem/chatroom
  src/main/java/com/ChattingSystem/chatroom/controller
  src/main/java/com/ChattingSystem/chatroom/dto
  src/main/java/com/ChattingSystem/chatroom/dto/request
  src/main/java/com/ChattingSystem/chatroom/dto/response
  src/main/java/com/ChattingSystem/chatroom/entity
  src/main/java/com/ChattingSystem/chatroom/repository
  src/main/java/com/ChattingSystem/chatroom/service
  src/main/java/com/ChattingSystem/chatroom/service/impl
  src/main/java/com/ChattingSystem/chatroom/util
  src/main/java/com/ChattingSystem/config
  src/main/java/com/ChattingSystem/message
  src/main/java/com/ChattingSystem/message/controller
  src/main/java/com/ChattingSystem/message/dto
  src/main/java/com/ChattingSystem/message/dto/request
  src/main/java/com/ChattingSystem/message/dto/response
  src/main/java/com/ChattingSystem/message/entity
  src/main/java/com/ChattingSystem/message/repository
  src/main/java/com/ChattingSystem/message/service
  src/main/java/com/ChattingSystem/message/service/impl
  src/main/java/com/ChattingSystem/post
  src/main/java/com/ChattingSystem/post/controller
  src/main/java/com/ChattingSystem/post/dto
  src/main/java/com/ChattingSystem/post/dto/request
  src/main/java/com/ChattingSystem/post/dto/response
  src/main/java/com/ChattingSystem/post/entity
  src/main/java/com/ChattingSystem/post/repository
  src/main/java/com/ChattingSystem/post/service
  src/main/java/com/ChattingSystem/post/service/impl
  src/main/java/com/ChattingSystem/post/util
  src/main/java/com/ChattingSystem/realtime
  src/main/java/com/ChattingSystem/realtime/config
  src/main/java/com/ChattingSystem/realtime/event
  src/main/java/com/ChattingSystem/realtime/security
  src/main/java/com/ChattingSystem/security
  src/main/java/com/ChattingSystem/security/config
  src/main/java/com/ChattingSystem/security/handler
  src/main/java/com/ChattingSystem/security/jwt
  src/main/java/com/ChattingSystem/shared
  src/main/java/com/ChattingSystem/shared/entity
  src/main/java/com/ChattingSystem/shared/exeption
  src/main/java/com/ChattingSystem/shared/properties
  src/main/java/com/ChattingSystem/shared/redis
  src/main/java/com/ChattingSystem/shared/response
  src/main/java/com/ChattingSystem/shared/util
  src/main/java/com/ChattingSystem/storage
  src/main/java/com/ChattingSystem/storage/config
  src/main/java/com/ChattingSystem/storage/controller
  src/main/java/com/ChattingSystem/storage/dto
  src/main/java/com/ChattingSystem/storage/dto/response
  src/main/java/com/ChattingSystem/storage/service
  src/main/java/com/ChattingSystem/storage/service/impl
  src/main/java/com/ChattingSystem/storage/util
  src/main/java/com/ChattingSystem/user
  src/main/java/com/ChattingSystem/user/controller
  src/main/java/com/ChattingSystem/user/dto
  src/main/java/com/ChattingSystem/user/dto/request
  src/main/java/com/ChattingSystem/user/dto/response
  src/main/java/com/ChattingSystem/user/entity
  src/main/java/com/ChattingSystem/user/repository
  src/main/java/com/ChattingSystem/user/service
  src/main/java/com/ChattingSystem/user/service/impl
  src/main/java/com/ChattingSystem/waitlist
  src/main/java/com/ChattingSystem/waitlist/controller
  src/main/java/com/ChattingSystem/waitlist/dto
  src/main/java/com/ChattingSystem/waitlist/dto/request
  src/main/java/com/ChattingSystem/waitlist/dto/response
  src/main/java/com/ChattingSystem/waitlist/entity
  src/main/java/com/ChattingSystem/waitlist/repository
  src/main/java/com/ChattingSystem/waitlist/service
  src/main/java/com/ChattingSystem/waitlist/service/impl
  ```
- Lệnh build và chạy test (`CLAUDE.md:12-18`, `AGENT.md:107-113`):
  - Chạy dev: `./mvnw spring-boot:run`
  - Build jar kèm test: `./mvnw clean package`
  - Build jar bỏ qua test: `./mvnw clean package -DskipTests`
  - Chạy toàn bộ test: `./mvnw test`
  - Chạy unit tests độc lập không phụ thuộc DB: `./mvnw test -Dtest='SlugUtilsTest,ReadingTimeCalculatorTest,HtmlSanitizerTest,ImageTypeTest,ChatRoomServiceUnitTest,MessageServiceUnitTest,AuthServiceUnitTest'`
  - Chạy 1 test cụ thể: `./mvnw test -Dtest=ChattingSystemApplicationTests`
- Thư mục tài liệu / file `.md` liên quan:
  - Thư mục `docs/`: `KHÔNG TÌM THẤY` (lệnh tìm: `Test-Path docs`).
  - Các file markdown có trong repo: `AGENT.md`, `CLAUDE.md`, `HELP.md` (đều nằm tại thư mục gốc).

Trích code (`pom.xml:5-10, 17-21`):
```xml
	<parent>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-parent</artifactId>
		<version>4.1.1</version>
		<relativePath/>
	</parent>

	<properties>
		<java.version>25</java.version>
		<aws-sdk.version>2.56.0</aws-sdk.version>
		<jsoup.version>1.21.2</jsoup.version>
	</properties>
```

Không tìm thấy: thư mục `docs/` hoặc các file thiết kế chuyên biệt khác ngoài `AGENT.md`, `CLAUDE.md`, `HELP.md`.

---

## A2. Cơ sở dữ liệu

- Thư mục Flyway: `src/main/resources/db/migration/` (`src/main/resources/application.yaml:35`)
- Số phiên bản migration cao nhất: **V9** (`V9__support_guest_users.sql`)
- 5 migration mới nhất:
  1. `V5__messages_and_room_privacy.sql` (11/09/2026)
  2. `V6__add_room_type.sql` (11/09/2026)
  3. `V7__turn_off_chatroom_ttl.sql` (11/09/2026)
  4. `V8__add_status_for_room.sql` (11/09/2026)
  5. `V9__support_guest_users.sql` (11/11/2026)
- Quy ước đặt tên file migration: `V{version}__{description}.sql` (tiền tố `V`, số thứ tự phiên bản nguyên tố, 2 dấu gạch dưới `__`, tên mô tả bằng snake_case dạng chữ thường).
- Kiểu kết nối Supabase:
  - Dùng Pooler (Transaction pooler của Supabase) qua cổng **6543** (suy luận từ cấu hình thực tế trong `.env`: `DB_HOST` trỏ về domain `<REDACTED>.pooler.supabase.com` và `DB_PORT=6543`).
  - Chuỗi URL JDBC: `jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}?sslmode=require&prepareThreshold=0` (`src/main/resources/application.yaml:14`).
  - Tham số pooler đặc biệt: có tham số **`prepareThreshold=0`** (vô hiệu hóa server-side prepared statement caching của PostgreSQL JDBC driver nhằm tương thích hoàn toàn với PgBouncer / Supabase Transaction Mode) và **`sslmode=require`**.
- DDL đầy đủ của các bảng (tổng hợp từ các file Flyway `V1` đến `V9`):

  **1. Bảng `users`** (từ `V1__init.sql:1-10`, `V2__roles_blog_waitlist.sql:2-6`, `V9__support_guest_users.sql:2-6`):
  ```sql
  CREATE TABLE users (
      id            BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
      full_name     VARCHAR(100) NOT NULL,
      firebase_uid  VARCHAR(128) UNIQUE,           -- ban đầu NOT NULL ở V1, sửa DROP NOT NULL ở V9
      email         VARCHAR(255) UNIQUE,           -- ban đầu NOT NULL ở V1, sửa DROP NOT NULL ở V9
      avatar_object VARCHAR(500),
      phone         VARCHAR(20),
      role          VARCHAR(20) NOT NULL DEFAULT 'USER',
      created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
      updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
      CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN', 'GUEST'))
  );
  ```

  **2. Bảng `chat_rooms`** (từ `V4__create_chat_rooms.sql:3-17`, `V5__messages_and_room_privacy.sql:2-15`, `V6__add_room_type.sql:1`, `V7__turn_off_chatroom_ttl.sql:1-2`):
  ```sql
  CREATE TABLE chat_rooms (
      id                BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
      title             VARCHAR(100) NOT NULL,
      description       VARCHAR(500),
      created_by        BIGINT NOT NULL REFERENCES users(id),
      default_live_time INT,                       -- ban đầu NOT NULL ở V2, DROP NOT NULL ở V7
      radius_m          INT NOT NULL DEFAULT 3000,
      status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
      expires_at        TIMESTAMP WITH TIME ZONE,  -- ban đầu NOT NULL ở V2, DROP NOT NULL ở V7
      center            GEOGRAPHY(Point, 4326),
      display_center    GEOGRAPHY(Point, 4326),    -- thêm ở V5
      room_type         VARCHAR(20) NOT NULL DEFAULT 'PUBLIC', -- thêm ở V6
      created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
      updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
      CONSTRAINT chk_chat_rooms_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'CLOSED'))
  );

  CREATE INDEX idx_chat_rooms_center ON chat_rooms USING GIST (center);
  CREATE INDEX idx_chat_rooms_display_center ON chat_rooms USING GIST (display_center);
  CREATE INDEX idx_chat_rooms_status_expires_at ON chat_rooms (status, expires_at);
  CREATE INDEX idx_chat_rooms_created_by ON chat_rooms (created_by);
  ```

  **3. Bảng `room_members`** (từ `V4__create_chat_rooms.sql:19-28`, `V5__messages_and_room_privacy.sql:17`, `V8__add_status_for_room.sql:1`):
  ```sql
  CREATE TABLE room_members (
      id         BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
      room_id    BIGINT NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
      user_id    BIGINT NOT NULL REFERENCES users(id),
      role       VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
      joined_at  TIMESTAMP WITH TIME ZONE NOT NULL,
      status     VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- thêm ở V8
      created_at TIMESTAMP WITH TIME ZONE NOT NULL,
      updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
      CONSTRAINT uq_room_members UNIQUE (room_id, user_id)
  );

  CREATE INDEX idx_room_members_user_id ON room_members (user_id);
  ```

  **4. Bảng `messages`** (từ `V5__messages_and_room_privacy.sql:20-34`):
  ```sql
  CREATE TABLE messages (
      id                BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
      room_id           BIGINT        NOT NULL REFERENCES chat_rooms (id) ON DELETE CASCADE,
      sender_id         BIGINT        NOT NULL REFERENCES users (id),
      client_message_id UUID          NOT NULL,
      type              VARCHAR(20)   NOT NULL DEFAULT 'TEXT',
      content           VARCHAR(2000) NOT NULL,
      deleted_at        TIMESTAMP WITH TIME ZONE,
      created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
      updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
      CONSTRAINT uk_messages_sender_client_id UNIQUE (sender_id, client_message_id),
      CONSTRAINT chk_messages_type CHECK (type IN ('TEXT', 'SYSTEM'))
  );

  CREATE INDEX idx_messages_room_id_id ON messages (room_id, id DESC);
  ```

  **5. Bảng guest**: `KHÔNG TÌM THẤY` bảng riêng.
  - SUY LUẬN: Căn cứ `V9__support_guest_users.sql:1-6` và `User.java:70-75`, hệ thống không tạo bảng riêng cho guest mà lưu trực tiếp vào bảng `users` với `role = 'GUEST'`, `email = NULL`, `firebase_uid = NULL`.
  
  **6. Mọi bảng liên quan tệp/ảnh đại diện/đính kèm**:
  - `KHÔNG TÌM THẤY` bảng riêng cho tệp đính kèm hoặc media upload (lệnh tìm: `Select-String "CREATE TABLE" src/main/resources/db/migration/*.sql`).
  - Các trường lưu trữ đường dẫn/key file trong DB hiện có:
    - Cột `users.avatar_object VARCHAR(500)` (`V1__init.sql:6`)
    - Cột `posts.cover_image_url VARCHAR(1000)` (`V2__roles_blog_waitlist.sql:15`)

- Quy ước chung:
  - Khóa chính: `BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY` (`BaseEntity.java:23-25`).
  - Kiểu thời gian: `TIMESTAMP WITH TIME ZONE` (`Instant` trong Java), cấu hình Hibernate `time_zone: UTC` (`application.yaml:24-25`).
  - Lưu enum: Chuỗi `@Enumerated(EnumType.STRING)` ánh xạ cột `VARCHAR(20)` có kèm ràng buộc `CHECK (col IN (...))`.
  - Xóa mềm: Bảng `messages` có cột `deleted_at TIMESTAMP WITH TIME ZONE` (`Message.java:39-40`, `V5:27`). Các bảng khác (`users`, `chat_rooms`, `room_members`) không có cột xóa mềm.
  - Đặt tên: snake_case cho toàn bộ bảng và cột. Tên bảng dùng danh từ số nhiều (`users`, `chat_rooms`, `room_members`, `messages`, `posts`, `tags`, `post_tags`, `waitlist_entries`).

Trích code (`src/main/resources/application.yaml:13-17`):
```yaml
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}?sslmode=require&prepareThreshold=0
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
```

Không tìm thấy: bảng `guest` riêng, bảng `attachments` hoặc `files`.

---

## A3. Xác thực và Guest

- `SecurityConfig` (`src/main/java/com/ChattingSystem/security/config/SecurityConfig.java:21-50`):
  - Servlet context-path: `/api/v1` (`application.yaml:4`). Các matcher trong `SecurityConfig` được tính tương đối theo servlet path này.
  - Endpoints public (`permitAll()`):
    - `POST /auth/login`
    - `POST /auth/guest`
    - `GET /posts`, `GET /posts/**`, `GET /tags`
    - `POST /waitlist`
    - `/error`
    - `/ws`, `/ws/**` (WebSocket HTTP handshake mở để STOMP interceptor tự xác thực)
    - `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`
  - Endpoints bảo vệ:
    - `/admin/**`: yêu cầu `hasAuthority("SCOPE_ADMIN")`
    - Tất cả request còn lại: `.anyRequest().authenticated()`

Trích code (`src/main/java/com/ChattingSystem/security/config/SecurityConfig.java:25-48`):
```java
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(POST, "/auth/login", "/auth/guest").permitAll()
                        .requestMatchers(GET, "/posts", "/posts/**", "/tags").permitAll()
                        .requestMatchers(POST, "/waitlist").permitAll()
                        .requestMatchers("/error").permitAll()
                        // Handshake WebSocket: JWT được kiểm tra ở frame STOMP CONNECT (StompAuthChannelInterceptor)
                        .requestMatchers("/ws", "/ws/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/admin/**").hasAuthority("SCOPE_ADMIN")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(
                        oauth2 -> oauth2
                                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                                .authenticationEntryPoint(authenticationEntryPoint)
                                .accessDeniedHandler(accessDeniedHandler));
```

- Filter / converter Firebase:
  - `KHÔNG TÌM THẤY` Filter hoặc Converter riêng cho Firebase trong Spring Security filter chain (lệnh tìm: `Select-String "Firebase" src/main/java/com/ChattingSystem/security`).
  - SUY LUẬN: Firebase ID Token không dùng để xác thực request liên tục. Client chỉ gửi Firebase ID token một lần lên `POST /api/v1/auth/login`. Tại `AuthServiceImpl.java:71`, server gọi `firebaseAuth.verifyIdToken(request.idToken())`. Khi token hợp lệ, server tự cấp Bearer JWT nội bộ ký bằng HS256 (`JwtService.java:26-52`). Toàn bộ API sau đó xác thực bằng Spring Security OAuth2 Resource Server qua `NimbusJwtDecoder` với symmetric key `JWT_SECRET_KEY` (`JwtConfig.java:27-32`).
- Class đại diện principal / current user:
  - Trong REST Controller: `org.springframework.security.oauth2.jwt.Jwt` thông qua annotation `@AuthenticationPrincipal Jwt jwt`.
  - Trong WebSocket: `java.security.Principal` (chứa `JwtAuthenticationToken`), được unpack qua tiện ích `StompPrincipals.userIdOf(principal)` (`StompPrincipals.java:14-19`).
  - Cách lấy `userId` trong Controller:
    ```java
    Long userId = ((Number) jwt.getClaim("userId")).longValue();
    ```
    (Thấy tại `ChatRoomController.java:47-49`, `MessageController.java:39, 50`, `AuthController.java:31-33, 46`).
- Guest được xác thực bằng cách nào:
  - Guest **không** dùng Firebase Anonymous, không dùng token riêng biệt khác định dạng mà dùng chung hệ thống JWT của app.
  - Khách gọi `POST /api/v1/auth/guest` (body tùy chọn `{ "nickname": "..." }`) (`AuthController.java:38-42`).
  - `guest_id` sinh ở đâu: Là cột `id` kiểu BIGINT auto-increment của PostgreSQL sinh ra khi lưu `User.createGuest(nickname)` vào bảng `users` (`AuthServiceImpl.java:176-177`).
  - `guest_id` lưu ở đâu: Lưu tại cột `users.id` với `role = Role.GUEST`, `firebase_uid = NULL`, `email = NULL`.
  - `guest_id` gửi lên server thế nào: Server cấp JWT có claim `userId` mang giá trị này và subject `guest:<id>` (`JwtService.java:29-43`). Client gửi JWT này trong header `Authorization: Bearer <token>` ở mọi request REST và frame STOMP CONNECT.
  - Luồng guest vào phòng public:
    1. Guest gửi `POST /api/v1/rooms/{roomId}/members` kèm header `Authorization: Bearer <guest_jwt>` và tọa độ `{ "lat": ..., "lng": ... }` (`ChatRoomController.java:93-100`).
    2. `ChatRoomServiceImpl.joinRoom`: kiểm tra `roomAccess.requireActiveRoom(roomId)`. Nếu phòng là `private` và user là guest, ném lỗi 403 `LOGIN_REQUIRED_FOR_PRIVATE_ROOM` (`ChatRoomServiceImpl.java:145-147`).
    3. Nếu là phòng `public`, kiểm tra tọa độ `isWithinRoomRadius`. Nếu trong bán kính, lưu `RoomMember` với `status = MemberStatus.ACTIVE`, `role = MemberRole.MEMBER` (`ChatRoomServiceImpl.java:166`).
    4. Bắn sự kiện realtime `MEMBER_JOINED` tới topic của phòng (`ChatRoomServiceImpl.java:171-173`).
- Xác thực WebSocket / STOMP:
  - Handshake endpoint `/api/v1/ws` là public trong `SecurityConfig:35`.
  - Xác thực nằm ở frame `CONNECT` của STOMP trong `StompAuthChannelInterceptor.java:58-61, 71-80`. Interceptor đọc native header `Authorization: Bearer <token>`, giải mã bằng `JwtDecoder` và gán `Principal` vào session.
  - Khi `SUBSCRIBE` vào `/topic/rooms.{roomId}`, interceptor kiểm tra `roomAccess.requireActiveMember(userId, roomId)` (`StompAuthChannelInterceptor.java:103`). Nếu không có quyền, frame bị hủy (`return null`) và gửi thông báo lỗi riêng tới `/user/queue/errors` thay vì ném STOMP ERROR (để tránh đóng socket) (`StompAuthChannelInterceptor.java:110-113`).

Trích code (`src/main/java/com/ChattingSystem/realtime/security/StompAuthChannelInterceptor.java:51-70`):
```java
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        return switch (accessor.getCommand()) {
            case CONNECT -> {
                accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
                yield message;
            }
            case SUBSCRIBE -> authorizeSubscribe(accessor, message);
            case SEND -> {
                requireUser(accessor.getUser());
                yield message;
            }
            default -> message;
        };
    }
```

Không tìm thấy: Filter xác thực Firebase trong Spring Security filter chain.

---

## A4. Phòng và quyền

- Entity / bảng phòng (`ChatRoom.java:21-47`, `V6__add_room_type.sql:1`):
  - Phân biệt public/private: Cột `room_type VARCHAR(20) NOT NULL DEFAULT 'PUBLIC'`, ánh xạ vào thuộc tính `private String roomType;` trong `ChatRoom.java:46` (giá trị chuỗi `"PUBLIC"`, `"PRIVATE"`; trong code dùng `"private".equalsIgnoreCase(...)`).
  - Các vai trò trong phòng: `MemberRole` enum (`OWNER`, `MEMBER`) (`MemberRole.java:3-6`).
  - Trạng thái phòng: `RoomStatus` enum (`ACTIVE`, `EXPIRED`, `CLOSED`) (`RoomStatus.java:3-7`).
  - Trạng thái thành viên: `MemberStatus` enum (`ACTIVE`, `REJECT`, `PENDING`) (`MemberStatus.java:3-7`).
- Chữ ký và thân hàm kiểm tra quyền:
  - Interface: `RoomAccessService.java:10, 13`:
    ```java
    ChatRoom requireActiveRoom(Long roomId);
    RoomMember requireActiveMember(Long userId, Long roomId);
    ```
  - Thân hàm trong `RoomAccessServiceImpl.java:28-47`:
  ```java
    @Override
    @Transactional(readOnly = true)
    public ChatRoom requireActiveRoom(Long roomId) {
        ChatRoom room = roomRepo.findById(roomId)
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));
        if (room.getStatus() != RoomStatus.ACTIVE
                || (room.getExpiresAt() != null && !room.getExpiresAt().isAfter(Instant.now()))) {
            throw new AppException(ErrorCode.ROOM_EXPIRED);
        }
        return room;
    }

    @Override
    @Transactional(readOnly = true)
    public RoomMember requireActiveMember(Long userId, Long roomId) {
        requireActiveRoom(roomId);
        return memberRepo.findByRoomIdAndUserIdAndStatus(roomId, userId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_ROOM_MEMBER));
    }
  ```
  - Kiểm tra riêng cho Guest và User:
    - Khi tạo phòng: `ChatRoomServiceImpl.java:66-68`:
      ```java
      if (user.isGuest()) {
          throw new AppException(ErrorCode.LOGIN_REQUIRED_TO_CREATE_ROOM);
      }
      ```
    - Khi tham gia phòng private: `ChatRoomServiceImpl.java:145-147`:
      ```java
      if (room != null && "private".equalsIgnoreCase(room.getRoomType()) && user.isGuest()) {
          throw new AppException(ErrorCode.LOGIN_REQUIRED_FOR_PRIVATE_ROOM);
      }
      ```
    - Khi gửi tin nhắn trong phòng private: `MessageServiceImpl.java:55-61`:
      ```java
      if (room != null && "private".equalsIgnoreCase(room.getRoomType())) {
          User user = userRepo.findById(userId)
                  .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
          if (user.isGuest()) {
              throw new AppException(ErrorCode.LOGIN_REQUIRED_TO_CHAT);
          }
      }
      ```
- Xử lý khi rời phòng, bị kick, xóa phòng:
  - **Rời phòng**: `RoomMemberServiceImpl.leaveRoom(userId, roomId)` (`RoomMemberServiceImpl.java:54-80`). Nếu `member.getRole() != MemberRole.MEMBER` (tức OWNER), ném 403 `OWNER_CANNOT_LEAVE_ROOM`. Nếu là MEMBER, thực hiện **xóa cứng** khỏi DB (`roomMemberRepository.delete(member)`), sau đó phát sự kiện realtime `MEMBER_LEFT`.
  - **Bị kick**: `KHÔNG TÌM THẤY` (lệnh tìm: `Select-String "kick"`). Không có API hay service xử lý chức năng đuổi thành viên khỏi phòng.
  - **Xóa phòng**: Ở tầng Application `KHÔNG TÌM THẤY` API hoặc method xóa phòng (lệnh tìm: `Select-String "deleteRoom"`). Ở tầng Database: bảng `room_members` và `messages` đều có khóa ngoại tham chiếu `chat_rooms(id)` với thuộc tính `ON DELETE CASCADE` (`V4__create_chat_rooms.sql:21`, `V5__messages_and_room_privacy.sql:22`). Do đó nếu bản ghi phòng bị xóa trực tiếp trong DB thì toàn bộ thành viên và tin nhắn của phòng sẽ bị xóa cứng tự động (cascade).

Trích code (`src/main/java/com/ChattingSystem/chatroom/service/impl/RoomMemberServiceImpl.java:70-76`):
```java
                if (member.getRole() != MemberRole.MEMBER) {
                        throw new AppException(
                                        ErrorCode.OWNER_CANNOT_LEAVE_ROOM);
                }

                roomMemberRepository.delete(member);
```

Không tìm thấy: tính năng kick thành viên (`kickMember`), tính năng xóa phòng (`deleteRoom`).

---

## A5. Tin nhắn và WebSocket

- Entity `Message` (`src/main/java/com/ChattingSystem/message/entity/Message.java:21-41`):
  - Enum loại tin nhắn: `MessageType` gồm 2 giá trị `TEXT`, `SYSTEM` (`MessageType.java:3-6`).
  - Cột nullable: Cột `deleted_at` (`deletedAt`) là nullable (`Message.java:39-40`). Các cột còn lại (`roomId`, `senderId`, `clientMessageId`, `type`, `content`, `createdAt`, `updatedAt`) đều là `nullable = false`.
- Luồng gửi tin nhắn từ đầu đến cuối:
  1. **Nhận tin nhắn**:
     - Qua WebSocket: Client gửi frame SEND tới `/app/rooms.{roomId}.send`. Nhận tại `@MessageMapping("rooms.{roomId}.send")` trong `MessageStompController.java:28-33`.
     - Qua REST fallback: Client gọi `POST /api/v1/rooms/{roomId}/messages` tại `MessageController.java:45-54`.
  2. **Xử lý nghiệp vụ** (`MessageServiceImpl.send` - `MessageServiceImpl.java:51-86`):
     - Kiểm tra người dùng là thành viên active: `roomAccess.requireActiveMember(userId, roomId)`.
     - Nếu phòng là private và user là guest: ném 403 `LOGIN_REQUIRED_TO_CHAT`.
     - Validate nội dung tin nhắn không rỗng, không quá 2000 ký tự.
     - Kiểm tra idempotency: tìm tin nhắn trùng qua `messageRepo.findBySenderIdAndClientMessageId(userId, req.clientMessageId())`. Nếu đã có, trả về tin cũ và không phát sự kiện.
  3. **Lưu DB**:
     - `Message saved = messageRepo.saveAndFlush(Message.text(roomId, userId, req.clientMessageId(), content));`
  4. **Phát realtime**:
     - Service publish nội bộ: `events.publishEvent(new RoomEvent(roomId, RoomEventType.MESSAGE, response));`
     - `RoomEventBroadcaster.java:21-24` lắng nghe sự kiện bằng `@TransactionalEventListener(fallbackExecution = true)` (chỉ chạy sau khi transaction đã commit thành công vào DB).
     - Destination phát tin: **`/topic/rooms.{roomId}`** qua lệnh:
       `messagingTemplate.convertAndSend("/topic/rooms." + roomId, event.payload());`
- Cấu hình WebSocket / Broker (`WebSocketConfig.java:18-62`):
  - Dùng **in-memory Simple Broker**: `registry.enableSimpleBroker("/topic", "/queue")` (`WebSocketConfig.java:51`).
  - Prefix ứng dụng: `/app`, prefix user: `/user`.
  - Heartbeat: 10,000 ms gửi/nhận (`WebSocketConfig.java:22, 52`).
  - Thiết kế cho nhiều instance backend: **KHÔNG hỗ trợ nhiều instance backend**. Ghi chú trong JavaDoc `WebSocketConfig.java:15-17` nói rõ: "Simple broker trong bộ nhớ: chỉ đúng khi chạy 1 instance; nhiều instance thì cần relay (Redis Pub/Sub hoặc RabbitMQ)".
- Idempotency tin nhắn:
  - DB áp dụng ràng buộc duy nhất: `CONSTRAINT uk_messages_sender_client_id UNIQUE (sender_id, client_message_id)` (`V5__messages_and_room_privacy.sql:30`).
  - Cả User và Guest đều có `userId` trong DB nên cơ chế áp dụng đồng nhất.
  - Khi client gửi lại cùng một `clientMessageId`, `MessageServiceImpl.java:72-79` tìm thấy bản ghi đã lưu, kiểm tra đúng phòng, trả lại DTO `MessageResponse` cũ mà không ném lỗi và không phát lại sự kiện realtime.
- Sửa / xóa tin nhắn:
  - Sửa tin nhắn: `KHÔNG TÌM THẤY` (lệnh tìm: `Select-String "updateMessage"`). Không có tính năng sửa tin.
  - Xóa tin nhắn: `KHÔNG TÌM THẤY` API hoặc method service xóa tin nhắn (lệnh tìm: `Select-String "deleteMessage"`). Bảng `messages` và entity `Message` có cột `deleted_at` / `deletedAt`, query lấy lịch sử `findSlice` có điều kiện `AND m.deletedAt IS NULL` (`MessageRepository.java:28`), nhưng chưa có logic cập nhật trường này.
- API lấy lịch sử tin nhắn:
  - `GET /api/v1/rooms/{roomId}/messages?before={messageId}&size={size}` (`MessageController.java:32-42`). Phân trang con trỏ newest-first (mặc định size = 30, tối đa 100).
- JSON mẫu của một tin nhắn trả về hiện tại (`MessageResponse` bọc trong `ApiResponse`):
  ```json
  {
    "statusCode": 200,
    "message": "Gửi tin nhắn thành công!",
    "data": {
      "id": 105,
      "roomId": 12,
      "clientMessageId": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
      "type": "TEXT",
      "content": "Xin chào cả phòng!",
      "sender": {
        "id": 42,
        "fullName": "Nguyễn Văn A",
        "avatarUrl": "blog/2026/11/avatar.webp"
      },
      "createdAt": "2026-11-17T01:15:30.123456Z"
    }
  }
  ```

Trích code (`src/main/java/com/ChattingSystem/realtime/event/RoomEventBroadcaster.java:16-24`):
```java
    public static String topicOf(Long roomId) {
        return "/topic/rooms." + roomId;
    }

    // Chỉ gửi khi dữ liệu đã commit, để client không nhận tin mà REST chưa đọc được
    @TransactionalEventListener(fallbackExecution = true)
    public void on(RoomEvent event) {
        messagingTemplate.convertAndSend(topicOf(event.roomId()), event.payload());
    }
```

Không tìm thấy: tính năng sửa tin nhắn, API xóa tin nhắn (soft/hard delete), broker relay cho multi-instance.

---

## A6. Hạ tầng dùng chung

- Toàn bộ `ErrorCode` (toàn bộ 34 giá trị enum trong `src/main/java/com/ChattingSystem/shared/exeption/ErrorCode.java:5-46`):
  ```java
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
  ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy phòng chat"),
  ROOM_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "Bạn chưa tham gia phòng chat này"),
  OWNER_CANNOT_LEAVE_ROOM(HttpStatus.FORBIDDEN, "Chủ phòng không thể rời khỏi phòng"),
  ROOM_EXPIRED(HttpStatus.GONE, "Phòng chat đã hết hạn hoặc không còn hoạt động"),
  ALREADY_JOINED_ROOM(HttpStatus.CONFLICT, "Bạn đã tham gia phòng chat này rồi"),
  NOT_ROOM_MEMBER(HttpStatus.FORBIDDEN, "Bạn không phải thành viên của phòng chat này"),
  OUT_OF_RANGE(HttpStatus.FORBIDDEN, "Bạn đang ở ngoài bán kính của phòng chat này"),
  ROOM_LIMIT_REACHED(HttpStatus.TOO_MANY_REQUESTS, "Bạn đã có tối đa 3 phòng chat đang hoạt động"),
  POST_SLUG_EXISTS(HttpStatus.CONFLICT, "Slug bài viết đã tồn tại"),
  INVALID_FILE(HttpStatus.BAD_REQUEST, "Tệp không hợp lệ. Chỉ chấp nhận ảnh JPEG, PNG, WebP, AVIF hoặc GIF"),
  FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Tệp vượt quá dung lượng cho phép (5 MB)"),
  STORAGE_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "Chưa cấu hình dịch vụ lưu trữ"),
  STORAGE_ERROR(HttpStatus.BAD_GATEWAY, "Không thể tải tệp lên dịch vụ lưu trữ"),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống"),
  ROOM_NOT_FOUND_OR_NOT_OWNER(HttpStatus.NOT_FOUND, "Không tìm thấy phòng chat hoặc bạn không phải chủ phòng"),
  ROOM_PRIVATE(HttpStatus.FORBIDDEN, "Phòng chat này là riêng tư, bạn không thể tham gia"),
  NOT_ROOM_OWNER(HttpStatus.FORBIDDEN, "Bạn không phải chủ phòng chat này"),
  INVALID_STATUS(HttpStatus.BAD_REQUEST, "Trạng thái không hợp lệ"),
  ROOM_MEMBER_PENDING(HttpStatus.FORBIDDEN, "Bạn đã gửi yêu cầu tham gia phòng chat này, vui lòng chờ chủ phòng phê duyệt"),
  LOGIN_REQUIRED_FOR_PRIVATE_ROOM(HttpStatus.FORBIDDEN, "Vui lòng đăng nhập tài khoản để tham gia phòng riêng tư"),
  LOGIN_REQUIRED_TO_CHAT(HttpStatus.FORBIDDEN, "Vui lòng đăng nhập tài khoản để gửi tin nhắn trong phòng riêng tư"),
  LOGIN_REQUIRED_TO_CREATE_ROOM(HttpStatus.FORBIDDEN, "Vui lòng đăng nhập tài khoản để tạo phòng chat");
  ```
- Package và chữ ký constructor của các class hạ tầng:
  - **`AppException`**: `com.ChattingSystem.shared.exeption.AppException` (`AppException.java:9-35`):
    - `public AppException(ErrorCode errorCode)`
    - `public AppException(ErrorCode errorCode, String message)`
    - `public AppException(ErrorCode errorCode, List<FieldErrorResponse> errors)`
    - `public static AppException fieldError(String field, String message)`
  - **`GlobalExceptionHandler`**: `com.ChattingSystem.shared.exeption.GlobalExceptionHandler` (`GlobalExceptionHandler.java:27-255`).
  - **`ApiResponse`**: `com.ChattingSystem.shared.response.ApiResponse<T>` (`ApiResponse.java:9-45`):
    - `private ApiResponse(int statusCode, String message, T data)`
    - `public static <T> ApiResponse<T> success(String message, T data)`
    - `public static ApiResponse<Void> success(String message)`
  - **`ErrorResponse`**: `com.ChattingSystem.shared.response.ErrorResponse` (`ErrorResponse.java:13-34`):
    - `public ErrorResponse(int statusCode, String message, String path, LocalDateTime timestamp, List<FieldErrorResponse> errors)`
- Quy ước thiết kế:
  - **DTO**: Sử dụng Java `record`, đặt trong `dto/request/` và `dto/response/`. Hậu tố chuẩn là `Request` và `Response` (ví dụ `CreateRoomRequest`, `RoomResponse`).
  - **Validation**: Jakarta Validation (`@Valid`, `@NotBlank`, `@NotNull`, `@Size`, `@Min`, `@Max`, `@DecimalMin`, `@DecimalMax`). Bắt lỗi tập trung tại `GlobalExceptionHandler`.
  - **Mapper**: Không dùng MapStruct (không có thư viện MapStruct trong `pom.xml`). Sử dụng mapping thủ công bằng constructor của record, JPQL constructor expression (`SELECT new com...(...)`), hoặc static method `from(...)`.
  - **Đặt tên Controller / Service**:
    - Controller đặt tên `<Feature>Controller` trong subpackage `controller/` (ví dụ `ChatRoomController`).
    - Service gồm interface `<Feature>Service` trong subpackage `service/` và class thực thi `<Feature>ServiceImpl` trong `service/impl/`.
    - Dependency Injection bằng constructor tường minh viết tay (không dùng `@Autowired` lên field, không dùng `@RequiredArgsConstructor` của Lombok).
  - **`@Transactional`**: Đặt trên các method nghiệp vụ tại tầng Service Implementation (`service/impl`). Controller và Repository không đặt `@Transactional`. Các method chỉ đọc được đánh dấu `@Transactional(readOnly = true)`.

Trích code (`src/main/java/com/ChattingSystem/shared/exeption/AppException.java:16-35`):
```java
    public AppException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage());
    }

    public AppException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.errors = List.of();
    }

    public AppException(ErrorCode errorCode, List<FieldErrorResponse> errors) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.errors = List.copyOf(errors);
    }

    /** 400 {@code VALIDATION_ERROR} with a single field error. */
    public static AppException fieldError(String field, String message) {
        return new AppException(ErrorCode.VALIDATION_ERROR, List.of(new FieldErrorResponse(field, message)));
    }
```

Không tìm thấy: MapStruct, class abstract controller/service dùng chung.

---

## A7. Redis

- Hiện nay dùng làm gì:
  - Đã import starter `spring-boot-starter-data-redis` (`pom.xml:122-124`), cấu hình URL qua `REDIS_URL` (`application.yaml:30-31`).
  - Đã viết class `RedisService` (`src/main/java/com/ChattingSystem/shared/redis/RedisService.java:16-77`) cung cấp các hàm `set`, `get`, `delete`, `exists`, và `getOrLoad` (có fallback gọi DB khi Redis offline).
  - **Hiện trạng thực tế trong code**: `KHÔNG TÌM THẤY` bất kỳ service nghiệp vụ nào đang inject hoặc gọi `RedisService` (lệnh tìm: `Select-String -Pattern "RedisService" src/main/java`). Redis mới chỉ được chuẩn bị sẵn hạ tầng mà chưa đưa vào sử dụng trong các tính năng hiện hữu.
- Template sử dụng: `StringRedisTemplate` kết hợp `tools.jackson.databind.json.JsonMapper` (`RedisService.java:17-18`).
- Quy ước đặt tên key: `KHÔNG TÌM THẤY` (vì chưa có service nào dùng nên chưa có pattern key cụ thể).
- Lua Script: `KHÔNG TÌM THẤY` (lệnh tìm: `Get-ChildItem -Filter "*.lua"`).
- Redis Pub/Sub: `KHÔNG TÌM THẤY` (lệnh tìm: `Select-String "RedisMessageListenerContainer|convertAndSend" src/main/java/com/ChattingSystem/shared/redis`).
- Có dùng cho phát tin WebSocket không: **KHÔNG**. WebSocket dùng in-memory Simple Broker nội bộ của Spring Boot (`WebSocketConfig.java:51`).

Trích code (`src/main/java/com/ChattingSystem/shared/redis/RedisService.java:25-39`):
```java
    public <T> void set(String key, T value, Duration ttl) {
        if(value == null){
            return;
        }
        String json = jsonMapper.writeValueAsString(value);
        redisTemplate.opsForValue().set(key,json,ttl);
    }

    public <T> Optional<T> get(String key, Class<T> clazz){
        String json = redisTemplate.opsForValue().get(key);
        if(json == null || json.isBlank()){
            return Optional.empty();
        }
        return Optional.of(jsonMapper.readValue(json, clazz));
    }
```

Không tìm thấy: vị trí inject `RedisService` trong mã nguồn nghiệp vụ, quy ước prefix key Redis, script Lua, Redis Pub/Sub.

---

## A8. Đồng thời và lập lịch

- `@EnableScheduling`: `KHÔNG TÌM THẤY` (lệnh tìm: `Select-String "EnableScheduling" src/main/java`).
- `@EnableAsync`: `KHÔNG TÌM THẤY` (lệnh tìm: `Select-String "EnableAsync" src/main/java`).
- `@Scheduled`: `KHÔNG TÌM THẤY` (lệnh tìm: `Select-String "@Scheduled" src/main/java`).
- ShedLock hoặc cơ chế khóa phân tán cho cron: `KHÔNG TÌM THẤY` (lệnh tìm: `Select-String "shedlock" pom.xml, src/main/java`).
- Virtual Threads (`spring.threads.virtual.enabled`): `KHÔNG TÌM THẤY` trong `application.yaml` (mặc định của Spring Boot là `false`).
- Kích thước pool HikariCP: `KHÔNG TÌM THẤY` trong `application.yaml` (không khai báo `spring.datasource.hikari.*`, dùng giá trị mặc định của Spring Boot/HikariCP là 10 kết nối).
- Các Executor / Thread Pool tự định nghĩa:
  - Chỉ có **1** bean duy nhất là `ThreadPoolTaskScheduler stompHeartbeatScheduler()` trong `WebSocketConfig.java:33-39`:
    - Pool size: `1`
    - Thread prefix: `"stomp-heartbeat-"`
    - Mục đích: quản lý heartbeat cho STOMP WebSocket.

Trích code (`src/main/java/com/ChattingSystem/realtime/config/WebSocketConfig.java:32-39`):
```java
    @Bean
    public ThreadPoolTaskScheduler stompHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("stomp-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }
```

Không tìm thấy: cron job `@Scheduled`, cấu hình `@Async`, ShedLock, cấu hình HikariCP tùy chỉnh.

---

## A9. Code upload/storage đã tồn tại

Bảng tổng hợp tìm kiếm các từ khóa liên quan đến lưu trữ:

| Từ khóa | Trạng thái | Chi tiết / Vị trí |
| :--- | :--- | :--- |
| `s3` | **Tìm thấy** | AWS SDK v2 S3 client: `StorageConfig.java:27` (`S3Client`), `S3StorageServiceImpl.java:64` (`s3Client.putObject`). |
| `minio` | `KHÔNG TÌM THẤY` | Lệnh tìm: `Select-String -Pattern "minio" src/ pom.xml`. |
| `r2` | **Tìm thấy** | Cloudflare R2 được dùng làm đích lưu trữ tương thích S3 (`StorageProperties.java:6`, `CLAUDE.md:37`). |
| `multipart` | **Tìm thấy** | Endpoint nhận file qua `MultipartFile`: `AdminUploadController.java:25-26`; giới hạn trong `application.yaml:39-41` (5 MB file, 6 MB request). |
| `upload` | **Tìm thấy** | Endpoint `POST /api/v1/admin/uploads/images` (`AdminUploadController.java:26`). |
| `attachment`| `KHÔNG TÌM THẤY` | Lệnh tìm: `Select-String -Pattern "attachment" src/ pom.xml`. Không có bảng hay endpoint nào về attachment tin nhắn. |
| `avatar` | **Tìm thấy** | Cột `users.avatar_object VARCHAR(500)` (`V1:6`), field `avatarObject` (`User.java:31`), hàm `updateAvatar` (`User.java:96`), DTO `SenderResponse.avatarUrl` (`SenderResponse.java:6`), `MeResponse.avatarObject` (`MeResponse.java:10`). Chưa có endpoint upload avatar riêng. |
| `presign` | `KHÔNG TÌM THẤY` | Lệnh tìm: `Select-String -Pattern "presign" src/ pom.xml`. Upload hiện tại đi qua backend server (`s3Client.putObject`), không dùng Presigned URL. |
| `file` | **Tìm thấy** | `MultipartFile` trong `AdminUploadController.java` và `S3StorageServiceImpl.java`. Xử lý kiểm tra dung lượng tối đa 5 MB (`MAX_IMAGE_SIZE_BYTES = 5L * 1024 * 1024`). |
| `storage` | **Tìm thấy** | Package `com.ChattingSystem.storage`, service `S3StorageServiceImpl.java`, properties `StorageProperties.java` (prefix `storage.*`). |
| `Tika` | `KHÔNG TÌM THẤY` | Lệnh tìm: `Select-String -Pattern "Tika" src/ pom.xml` và `mvn dependency:tree`. |
| `ffmpeg` | `KHÔNG TÌM THẤY` | Lệnh tìm: `Select-String -Pattern "ffmpeg" src/ pom.xml`. |
| `thumbnail` | `KHÔNG TÌM THẤY` | Lệnh tìm: `Select-String -Pattern "thumbnail" src/ pom.xml`. |

- Cách thức hoạt động của tính năng upload hiện tại:
  1. Endpoint `POST /api/v1/admin/uploads/images` (chỉ dành cho ADMIN có quyền `SCOPE_ADMIN`).
  2. Client gửi multipart request có tham số `file`.
  3. `S3StorageServiceImpl.java:40-79` kiểm tra:
     - Kiểm tra file rỗng, file vượt quá 5 MB (`MAX_IMAGE_SIZE_BYTES`).
     - Kiểm tra magic bytes của mảng byte đầu vào thông qua hàm `ImageType.detect(bytes)` (`ImageType.java:34-54`). Định dạng hỗ trợ: JPEG, PNG, GIF, WEBP, AVIF.
     - Khóa lưu trữ (S3 object key) sinh theo mẫu: `blog/{yyyy}/{MM}/{uuid}.{ext}` (`S3StorageServiceImpl.java:81-85`).
     - Gọi `s3Client.putObject` trực tiếp đẩy byte lên bucket Cloudflare R2 với `Cache-Control: public, max-age=31536000, immutable`.
     - Trả về `UploadResponse` chứa `url` (`STORAGE_PUBLIC_BASE_URL + "/" + key`) và `key`.

Trích code (`src/main/java/com/ChattingSystem/storage/service/impl/S3StorageServiceImpl.java:62-79`):
```java
        String key = buildKey(imageType);
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(storageProperties.bucket())
                            .key(key)
                            .contentType(imageType.contentType())
                            .contentLength((long) bytes.length)
                            .cacheControl(CACHE_CONTROL)
                            .build(),
                    RequestBody.fromBytes(bytes));
        } catch (SdkException exception) {
            log.error("Upload to bucket {} failed for key {}", storageProperties.bucket(), key, exception);
            throw new AppException(ErrorCode.STORAGE_ERROR);
        }

        return new UploadResponse(storageProperties.publicBaseUrl() + "/" + key, key);
```

Không tìm thấy: upload qua presigned URL, kiểm tra MIME bằng Apache Tika, xử lý video qua ffmpeg, sinh thumbnail, bảng lưu trữ media/attachment.

---

## A10. Dependencies

- Nguyên văn khối `<properties>`, `<dependencyManagement>`, `<dependencies>` từ `pom.xml`:

```xml
	<properties>
		<java.version>25</java.version>
		<aws-sdk.version>2.56.0</aws-sdk.version>
		<jsoup.version>1.21.2</jsoup.version>
	</properties>

	<dependencyManagement>
		<dependencies>
			<dependency>
				<groupId>software.amazon.awssdk</groupId>
				<artifactId>bom</artifactId>
				<version>${aws-sdk.version}</version>
				<type>pom</type>
				<scope>import</scope>
			</dependency>
		</dependencies>
	</dependencyManagement>

	<dependencies>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-jpa</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-security</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-validation</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-webmvc</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-websocket</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-security-oauth2-resource-server</artifactId>
		</dependency>

		<!-- Flyway -->
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-flyway</artifactId>
		</dependency>
		<!-- THÊM MỚI: module hỗ trợ PostgreSQL cho Flyway (sửa lỗi Unsupported Database) -->
		<dependency>
			<groupId>org.flywaydb</groupId>
			<artifactId>flyway-database-postgresql</artifactId>
		</dependency>

		<dependency>
			<groupId>org.postgresql</groupId>
			<artifactId>postgresql</artifactId>
			<scope>runtime</scope>
		</dependency>

		<dependency>
			<groupId>com.google.firebase</groupId>
			<artifactId>firebase-admin</artifactId>
			<version>9.11.0</version>
		</dependency>

		<!-- S3-compatible object storage (Cloudflare R2) -->
		<dependency>
			<groupId>software.amazon.awssdk</groupId>
			<artifactId>s3</artifactId>
			<exclusions>
				<exclusion>
					<groupId>software.amazon.awssdk</groupId>
					<artifactId>netty-nio-client</artifactId>
				</exclusion>
			</exclusions>
		</dependency>

		<!-- HTML sanitizing for blog post bodies -->
		<dependency>
			<groupId>org.jsoup</groupId>
			<artifactId>jsoup</artifactId>
			<version>${jsoup.version}</version>
		</dependency>

		<dependency>
			<groupId>org.projectlombok</groupId>
			<artifactId>lombok</artifactId>
			<optional>true</optional>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-devtools</artifactId>
			<scope>runtime</scope>
			<optional>true</optional>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-docker-compose</artifactId>
			<scope>runtime</scope>
			<optional>true</optional>
		</dependency>

		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-redis</artifactId>
		</dependency>

		<dependency>
			<groupId>org.apache.commons</groupId>
			<artifactId>commons-pool2</artifactId>
		</dependency>
		<!-- Test -->
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-jpa-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-security-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-validation-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-webmvc-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-websocket-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-security-oauth2-resource-server-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-flyway-test</artifactId>
			<scope>test</scope>
		</dependency>

		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-redis-test</artifactId>
			<scope>test</scope>
		</dependency>

		<dependency>
			<groupId>org.springdoc</groupId>
			<artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
			<version>3.2.0</version>
		</dependency>
	</dependencies>
```

- Kết quả thực thi lệnh `mvn dependency:tree -Dincludes=software.amazon.awssdk,com.fasterxml.jackson.core,tools.jackson.core,org.apache.httpcomponents,org.apache.httpcomponents.client5,io.netty,org.apache.tika`:

```text
[INFO] Scanning for projects...
[INFO] 
[INFO] -------------------------< com:ChattingSystem >-------------------------
[INFO] Building ChattingSystem 0.0.1-SNAPSHOT
[INFO]   from pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- dependency:3.10.0:tree (default-cli) @ ChattingSystem ---
[INFO] com:ChattingSystem:jar:0.0.1-SNAPSHOT
[INFO] +- com.google.firebase:firebase-admin:jar:9.11.0:compile
[INFO] |  +- com.google.api-client:google-api-client:jar:2.7.2:compile
[INFO] |  |  +- org.apache.httpcomponents:httpcore:jar:4.4.16:compile
[INFO] |  |  \- org.apache.httpcomponents:httpclient:jar:4.5.14:compile
[INFO] |  +- com.google.cloud:google-cloud-storage:jar:2.73.0:compile
[INFO] |  |  +- com.fasterxml.jackson.core:jackson-databind:jar:2.21.5:compile
[INFO] |  |  +- com.fasterxml.jackson.core:jackson-annotations:jar:2.21:compile
[INFO] |  |  \- com.fasterxml.jackson.core:jackson-core:jar:2.21.5:compile
[INFO] |  +- io.netty:netty-codec-http:jar:4.2.17.Final:compile
[INFO] |  |  +- io.netty:netty-common:jar:4.2.17.Final:compile
[INFO] |  |  +- io.netty:netty-buffer:jar:4.2.17.Final:compile
[INFO] |  |  +- io.netty:netty-codec-base:jar:4.2.17.Final:compile
[INFO] |  |  \- io.netty:netty-codec-compression:jar:4.2.17.Final:compile
[INFO] |  +- io.netty:netty-handler:jar:4.2.17.Final:compile
[INFO] |  |  +- io.netty:netty-resolver:jar:4.2.17.Final:compile
[INFO] |  |  \- io.netty:netty-transport-native-unix-common:jar:4.2.17.Final:compile
[INFO] |  +- io.netty:netty-transport:jar:4.2.17.Final:compile
[INFO] |  \- org.apache.httpcomponents.client5:httpclient5:jar:5.6.4:compile
[INFO] +- software.amazon.awssdk:s3:jar:2.56.0:compile
[INFO] |  +- software.amazon.awssdk:aws-xml-protocol:jar:2.56.0:compile
[INFO] |  |  \- software.amazon.awssdk:aws-query-protocol:jar:2.56.0:compile
[INFO] |  +- software.amazon.awssdk:protocol-core:jar:2.56.0:compile
[INFO] |  +- software.amazon.awssdk:arns:jar:2.56.0:compile
[INFO] |  +- software.amazon.awssdk:profiles:jar:2.56.0:compile
[INFO] |  +- software.amazon.awssdk:crt-core:jar:2.56.0:compile
[INFO] |  +- software.amazon.awssdk:http-auth:jar:2.56.0:compile
[INFO] |  +- software.amazon.awssdk:identity-spi:jar:2.56.0:compile
[INFO] |  +- software.amazon.awssdk:http-auth-spi:jar:2.56.0:compile
[INFO] |  +- software.amazon.awssdk:http-auth-aws:jar:2.56.0:compile
[INFO] |  +- software.amazon.awssdk:checksums:jar:2.56.0:compile
[INFO] |  +- software.amazon.awssdk:checksums-spi:jar:2.56.0:compile
[INFO] |  +- software.amazon.awssdk:retries-spi:jar:2.56.0:compile
[INFO] |  +- software.amazon.awssdk:sdk-core:jar:2.56.0:compile
[INFO] |  |  \- software.amazon.awssdk:retries:jar:2.56.0:compile
[INFO] |  +- software.amazon.awssdk:auth:jar:2.56.0:compile
[INFO] |  |  \- software.amazon.awssdk:http-auth-aws-eventstream:jar:2.56.0:compile
[INFO] |  +- software.amazon.awssdk:http-client-spi:jar:2.56.0:compile
[INFO] |  +- software.amazon.awssdk:regions:jar:2.56.0:compile
[INFO] |  +- software.amazon.awssdk:annotations:jar:2.56.0:compile
[INFO] |  +- software.amazon.awssdk:utils:jar:2.56.0:compile
[INFO] |  +- software.amazon.awssdk:aws-core:jar:2.56.0:compile
[INFO] |  |  \- software.amazon.awssdk:utils-lite:jar:2.56.0:compile
[INFO] |  +- software.amazon.awssdk:metrics-spi:jar:2.56.0:compile
[INFO] |  +- software.amazon.awssdk:json-utils:jar:2.56.0:compile
[INFO] |  |  \- software.amazon.awssdk:third-party-jackson-core:jar:2.56.0:compile
[INFO] |  +- software.amazon.awssdk:endpoints-spi:jar:2.56.0:compile
[INFO] |  \- software.amazon.awssdk:apache5-client:jar:2.56.0:runtime
[INFO] +- org.springframework.boot:spring-boot-docker-compose:jar:4.1.1:runtime (optional)
[INFO] |  \- tools.jackson.core:jackson-databind:jar:3.1.5:compile
[INFO] |     \- tools.jackson.core:jackson-core:jar:3.1.5:compile
[INFO] \- org.springframework.boot:spring-boot-starter-data-redis:jar:4.1.1:compile
[INFO]    \- org.springframework.boot:spring-boot-data-redis:jar:4.1.1:compile
[INFO]       \- io.lettuce:lettuce-core:jar:7.5.2.RELEASE:compile
[INFO]          \- io.netty:netty-resolver-dns:jar:4.2.17.Final:compile
[INFO]             \- io.netty:netty-codec-dns:jar:4.2.17.Final:compile
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

---

## B1. Cấu hình

- File cấu hình `src/main/resources/application.yaml` (toàn bộ, đã che bí mật):

```yaml
server:
  port: ${PORT:8080}
  servlet:
    context-path: /api/v1

spring:
  application:
    name: ChattingSystem

  config:
    import: optional:file:./.env[.properties]

  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}?sslmode=require&prepareThreshold=0
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        jdbc:
          time_zone: UTC
        default_batch_fetch_size: 50
    open-in-view: false

  data:
    redis:
      url: ${REDIS_URL:redis://localhost:6379}

  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 6MB

  docker:
    compose:
      enabled: false

firebase:
  service-account: ${FIREBASE_SERVICE_ACCOUNT}

jwt:
  secret-key: ${JWT_SECRET_KEY}
  access-token-expiration: ${JWT_ACCESS_EXPIRATION:15m}
  refresh-expiration: ${JWT_REFRESH_EXPIRATION:30d}

cors:
  allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:5173,https://chatchit.me,https://www.chatchit.me,http://192.168.50.199:3000}

storage:
  endpoint: ${STORAGE_ENDPOINT:}
  region: ${STORAGE_REGION:auto}
  bucket: ${STORAGE_BUCKET:}
  access-key: ${STORAGE_ACCESS_KEY:}
  secret-key: ${STORAGE_SECRET_KEY:}
  public-base-url: ${STORAGE_PUBLIC_BASE_URL:}
```

- Danh sách TÊN biến môi trường (từ `application.yaml` và `.env.example`):
  - `PORT`: Cổng máy chủ (mặc định 8080, Railway tự inject).
  - `DB_HOST`: Host kết nối PostgreSQL / Supabase pooler.
  - `DB_PORT`: Cổng kết nối PostgreSQL (mặc định 6543 cho Supabase Transaction Pooler).
  - `DB_NAME`: Tên database.
  - `DB_USERNAME`: Tên người dùng database.
  - `DB_PASSWORD`: Mật khẩu database.
  - `REDIS_URL`: Chuỗi kết nối Redis (mặc định `redis://localhost:6379`).
  - `FIREBASE_SERVICE_ACCOUNT`: Nội dung thô của JSON service account Firebase.
  - `JWT_SECRET_KEY`: Khóa bí mật HMAC-SHA256 (tối thiểu 32 bytes).
  - `JWT_ACCESS_EXPIRATION`: Thời hạn sống của access token (mặc định `15m`).
  - `JWT_REFRESH_EXPIRATION`: Thời hạn sống của refresh token (mặc định `30d`).
  - `CORS_ALLOWED_ORIGINS`: Danh sách origin được phép truy cập (phân tách bởi dấu phẩy).
  - `STORAGE_ENDPOINT`: Endpoint Cloudflare R2 / S3-compatible (nếu để trống, tính năng upload trả về 503 `STORAGE_NOT_CONFIGURED`).
  - `STORAGE_REGION`: Vùng lưu trữ S3 (mặc định `auto`).
  - `STORAGE_BUCKET`: Tên bucket lưu trữ.
  - `STORAGE_ACCESS_KEY`: Access Key ID của R2 / S3.
  - `STORAGE_SECRET_KEY`: Secret Access Key của R2 / S3.
  - `STORAGE_PUBLIC_BASE_URL`: URL public trỏ tới bucket (r2.dev hoặc custom domain, không có dấu `/` ở cuối).
- Danh sách Profile: Trong repo chỉ có file `application.yaml` chạy mặc định (`default` profile). Không có file `application-prod.yaml`, `application-dev.yaml` hay `application-test.yaml` (`KHÔNG TÌM THẤY`).

---

## B2. Docker / CI

- `Dockerfile` (`Dockerfile:1-11`):
  ```dockerfile
  FROM eclipse-temurin:25-jre
  WORKDIR /app

  RUN groupadd --system app && useradd --system --gid app app

  COPY target/*.jar app.jar

  USER app

  EXPOSE 8080
  ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
  ```

- `compose.yaml` (`compose.yaml:1-9`):
  ```yaml
  services:
    postgres:
      image: 'postgres:latest'
      environment:
        - 'POSTGRES_DB=mydatabase'
        - 'POSTGRES_PASSWORD=secret'
        - 'POSTGRES_USER=myuser'
      ports:
        - '5432'
  ```

- File cấu hình triển khai Railway (`railway.toml:1-10`):
  ```toml
  [build]
  builder = "DOCKERFILE"
  dockerfilePath = "Dockerfile"

  [deploy]
  # Public endpoint that also touches the DB, so a deploy only goes live once Postgres is reachable
  healthcheckPath = "/api/v1/tags"
  healthcheckTimeout = 300
  restartPolicyType = "ON_FAILURE"
  restartPolicyMaxRetries = 5
  ```

- Workflow CI: `KHÔNG TÌM THẤY` (lệnh kiểm tra: `Test-Path .github`). Không có thư mục `.github/workflows` hay file CI như `.gitlab-ci.yml`, `Jenkinsfile`.
- Dấu hiệu triển khai:
  - Triển khai trên **Railway** (căn cứ file `railway.toml`).
  - Kubernetes manifests: `KHÔNG TÌM THẤY`.
  - Terraform: `KHÔNG TÌM THẤY`.
  - Deployment scripts: `KHÔNG TÌM THẤY`.

---

## B3. Frontend

- Vị trí: **Repo riêng** (`KHÔNG TÌM THẤY` mã nguồn frontend hoặc file `package.json` trong repo `ChattingSystem`).
- Căn cứ thông tin từ backend:
  - Framework frontend: **Next.js** (căn cứ `CLAUDE.md:71`: *"The API contract shared with the Next.js frontend defines the paths/field names above"*).
  - Trình soạn thảo văn bản: **Tiptap** editor (căn cứ `CLAUDE.md:65` và `V3__post_content_html.sql:1`).
  - Cổng dev server: Cổng **3000** (Next.js mặc định) và cổng **5173** (Vite dev server) (căn cứ CORS origins trong `application.yaml:56`).
  - Origin dev / prod:
    - Dev: `http://localhost:3000`, `http://localhost:5173`, `http://192.168.50.199:3000`
    - Prod: `https://chatchit.me`, `https://www.chatchit.me`
  - Giao thức gọi WebSocket: STOMP over plain WebSocket tại endpoint `/api/v1/ws`.
  - Code upload hiện có ở frontend: Không nằm trong repo này.

---

## B4. Test

- Framework kiểm thử: **JUnit 5** (Jupiter), **Mockito**, **AssertJ**, **Spring Boot Test** (`spring-boot-starter-*-test`).
- Testcontainers: `KHÔNG TÌM THẤY` (lệnh tìm: `Select-String "testcontainers" pom.xml, src/test`).
- Cấu trúc thư mục test (`src/test/java/com/ChattingSystem/`):
  - **Unit tests** (chạy độc lập, không cần kết nối Database):
    - `auth/AuthServiceUnitTest.java`
    - `chatroom/ChatRoomServiceUnitTest.java`
    - `chatroom/RoomAccessServiceUnitTest.java`
    - `chatroom/RoomMemberServiceUnitTest.java`
    - `chatroom/util/LocationUtilsTest.java`
    - `message/MessageServiceUnitTest.java`
    - `post/util/HtmlSanitizerTest.java`
    - `post/util/ReadingTimeCalculatorTest.java`
    - `shared/util/SlugUtilsTest.java`
    - `storage/util/ImageTypeTest.java`
  - **Integration tests** (cần môi trường PostgreSQL + PostGIS và Redis thực tế):
    - `ChattingSystemApplicationTests.java`
    - `chatroom/ChatRoomServiceTest.java`
    - `chatroom/RoomMemberServiceTest.java`
    - `message/MessageServiceTest.java`
    - `realtime/StompMessagingTest.java`
- Lệnh chạy test:
  - Chạy toàn bộ test: `./mvnw test`
  - Chạy riêng unit test không phụ thuộc DB:
    ```bash
    ./mvnw test -Dtest='SlugUtilsTest,ReadingTimeCalculatorTest,HtmlSanitizerTest,ImageTypeTest,ChatRoomServiceUnitTest,MessageServiceUnitTest,AuthServiceUnitTest'
    ```

---

## B5. Quan sát

- Spring Boot Actuator: `KHÔNG TÌM THẤY` (lệnh tìm: `Select-String "actuator" pom.xml`).
- Micrometer / Prometheus: `KHÔNG TÌM THẤY` (lệnh tìm: `Select-String "micrometer" pom.xml`).
- Định dạng log:
  - Dùng **SLF4J** thông qua annotation `@Slf4j` của Lombok trên các class: `GlobalExceptionHandler`, `S3StorageServiceImpl`, `RedisService`, `FirebaseConfig`.
  - Định dạng console log chuẩn mặc định của Spring Boot. Không có file cấu hình tùy chỉnh riêng như `logback-spring.xml` (`KHÔNG TÌM THẤY`).
  - Healthcheck deploy dùng endpoint nghiệp vụ: `GET /api/v1/tags` (`railway.toml:7`).

---

## B6. Ràng buộc / quy mô

- Ràng buộc phòng chat:
  - Một người dùng chỉ được tạo tối đa **3 phòng đang hoạt động** cùng lúc (`ChatRoomServiceImpl.java:40, 70-73`: `MAX_ACTIVE_ROOMS_PER_USER = 3`, vi phạm ném 429 `ROOM_LIMIT_REACHED`).
  - Bán kính phòng mặc định: **3000 m** (`ChatRoomServiceImpl.java:39`).
  - Bán kính tìm kiếm xung quanh: tối thiểu **100 m**, tối đa **50,000 m** (50 km) (`ChatRoomController.java:77`).
- Ràng buộc tin nhắn:
  - Độ dài nội dung tin nhắn: tối đa **2000 ký tự** (`MessageServiceImpl.java:29, 67-69`, `messages.content VARCHAR(2000)`).
  - Phân trang lịch sử tin nhắn: tối đa **100 tin/lần**, mặc định **30 tin** (`MessageController.java:37, 40`).
- Ràng buộc tệp tin / hình ảnh:
  - Dung lượng file tối đa: **5 MB** (`S3StorageServiceImpl.java:27`: `MAX_IMAGE_SIZE_BYTES = 5L * 1024 * 1024`, `application.yaml:40`: `max-file-size: 5MB`).
  - Dung lượng request tối đa: **6 MB** (`application.yaml:41`: `max-request-size: 6MB`).
  - Định dạng ảnh được phép: chỉ chấp nhận ảnh có magic bytes thuộc các kiểu **JPEG, PNG, GIF, WEBP, AVIF** (`ImageType.java:10-14`).
- Ràng buộc bảo mật & thời gian:
  - STOMP Heartbeat: **10,000 ms** (`WebSocketConfig.java:22`).
  - Thời hạn JWT Access Token: mặc định **15 phút** (`15m`).
  - Thời hạn JWT Refresh Token: mặc định **30 ngày** (`30d`).
  - **Bảo mật vị trí địa lý tuyệt đối**: Tọa độ chính xác (`center`) của phòng chat không bao giờ được gửi về client; chỉ trả về khoảng cách mờ (`distanceLabel`) tính từ tâm đã làm mờ (`display_center` lệch ngẫu nhiên 50-150 m và làm tròn lưới 0.001°) (`CLAUDE.md:68`, `ChatRoomRepository.java:36-57`, `LocationFuzzer.java`).

---

## C. Thực nghiệm (Tùy chọn)

- Trạng thái thực hiện: **BỎ QUA / KHÔNG THỰC HIỆN ĐƯỢC**.
- Lý do kỹ thuật:
  - Khi kiểm tra lệnh `docker info` trên hệ thống, daemon Docker không hoạt động (lỗi nguyên văn: `failed to connect to the docker API at npipe:////./pipe/docker_engine; check if the path is correct and if the daemon is running: open //./pipe/docker_engine: The system cannot find the file specified`).
  - Không thể khởi chạy container MinIO cục bộ để tiến hành các bài test thực nghiệm tạo presigned URL, upload multipart hay OPTIONS preflight.

---

## CHƯA XÁC ĐỊNH

1. **Cấu hình chi tiết của Supabase Pooler phía server**: Mặc dù client cấu hình cổng 6543 và `prepareThreshold=0`, các tham số phía server Supabase (như `max_client_conn`, `pool_mode` transaction hay session) không nằm trong repo mã nguồn này.
2. **Mã nguồn và thư viện client Frontend**: Frontend nằm ở repository riêng, do đó không thể xác định chính xác các phiên bản cụ thể của thư viện STOMP client (`@stomp/stompjs` hay SockJS) hoặc cấu hình upload file phía client ngoài thông tin gián tiếp trong docs và CORS backend.
3. **Mục đích sử dụng thực tế của Redis**: `RedisService` đã được xây dựng hoàn chỉnh với Jackson v3 nhưng hiện tại chưa được inject vào bất kỳ class nào trong backend, chưa xác định được dự định cache dữ liệu gì trong tương lai (session, tin nhắn hay presence).
4. **Cơ chế Refresh Token**: Trong `application.yaml` có `JWT_REFRESH_EXPIRATION: 30d` và class helper `HashToken` nhưng chưa có controller/service nào triển khai endpoint refresh token.
5. **Cơ chế Soft Delete tin nhắn**: Cột `deleted_at` có trong DB và entity `Message`, query `findSlice` có điều kiện lọc `deletedAt IS NULL`, nhưng chưa xác định được luồng xóa tin nhắn (ai có quyền xóa, xóa bằng API nào).

---

## MÂU THUẪN PHÁT HIỆN

1. **Phiên bản Jackson v2 vs Jackson v3**:
   - `CLAUDE.md:7` nêu: *"Jackson is v3 (`tools.jackson.*`; annotations still `com.fasterxml.jackson.annotation`)"*.
   - Trong code: `RedisService.java:4, 8` dùng `tools.jackson.*`, nhưng dependency tree thực tế cho thấy `com.google.cloud:google-cloud-storage` (từ `firebase-admin:9.11.0`) vẫn kéo `com.fasterxml.jackson.core:jackson-databind:2.21.5` chạy song song với `tools.jackson.core:jackson-databind:3.1.5` (từ Spring Boot 4.1.1).
2. **Kiểu dữ liệu và ràng buộc cột `room_type` trong `chat_rooms`**:
   - Trong `V6__add_room_type.sql:1`: `room_type VARCHAR(20) NOT NULL DEFAULT 'PUBLIC'`.
   - Trong entity `ChatRoom.java:46`: `private String roomType; // "public" | "private" | "protected"` (dùng kiểu `String`, không tạo `Enum` class; comment đề cập cả `"protected"` nhưng không có code hay DB constraint nào xử lý `"protected"`).
   - Trong `ChatRoomServiceImpl.java:145, 163`: Code so sánh chuỗi `"private".equalsIgnoreCase(...)`, trong khi khi tạo mặc định hoa `"PUBLIC"` (`ChatRoom.java:59, 70`).
3. **Tên trường ảnh đại diện (Avatar) giữa các tầng**:
   - Trong DB: `avatar_object VARCHAR(500)` (`V1__init.sql:6`).
   - Trong `User.java:30`: `avatarObject`.
   - Trong `MeResponse.java:10`: `String avatarObject`.
   - Trong `SenderResponse.java:6` và `RoomMemberResponse.java:11`: lại đổi tên thành `String avatarUrl`.
4. **Cột `deleted_at` trong `messages` vs Code nghiệp vụ**:
   - Migration `V5__messages_and_room_privacy.sql:27` và `Message.java:39` định nghĩa cột `deleted_at`.
   - Query lấy tin nhắn trong `MessageRepository.java:28` có điều kiện `AND m.deletedAt IS NULL`.
   - Tuy nhiên trong `MessageService` và `MessageController` hoàn toàn không có method hay endpoint nào để cập nhật `deleted_at` hoặc xóa tin nhắn.
5. **Spring Boot Version 4.1.1**:
   - `pom.xml:8` khai báo `spring-boot-starter-parent` phiên bản `4.1.1`. Spring Boot 4.x hiện tại là phiên bản thử nghiệm/tương lai (Spring Boot chính thức hiện tại là dòng 3.x), dẫn đến việc dùng Jackson 3 (`tools.jackson`) vốn khác biệt so với chuẩn Spring Boot 3.x.
