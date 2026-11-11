package com.ChattingSystem.auth;

import com.ChattingSystem.auth.dto.request.FirebaseLoginRequest;
import com.ChattingSystem.auth.dto.request.GuestLoginRequest;
import com.ChattingSystem.auth.dto.response.LoginResult;
import com.ChattingSystem.auth.service.impl.AuthServiceImpl;
import com.ChattingSystem.chatroom.repository.ChatRoomRepository;
import com.ChattingSystem.chatroom.repository.RoomMemberRepository;
import com.ChattingSystem.message.repository.MessageRepository;
import com.ChattingSystem.security.jwt.JwtService;
import com.ChattingSystem.shared.exeption.AppException;
import com.ChattingSystem.shared.exeption.ErrorCode;
import com.ChattingSystem.shared.properties.JwtProperties;
import com.ChattingSystem.user.entity.Role;
import com.ChattingSystem.user.entity.User;
import com.ChattingSystem.user.repository.UserRepository;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceUnitTest {

    @Mock
    private FirebaseAuth firebaseAuth;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private JwtDecoder jwtDecoder;

    @Mock
    private RoomMemberRepository roomMemberRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    private AuthServiceImpl authService;

    private final JwtProperties jwtProperties = new JwtProperties(
            "test-secret-key-that-is-at-least-32-chars-long",
            Duration.ofHours(1),
            Duration.ofDays(7)
    );

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                firebaseAuth,
                userRepository,
                jwtService,
                jwtProperties,
                jwtDecoder,
                roomMemberRepository,
                messageRepository,
                chatRoomRepository
        );
    }

    private FirebaseToken mockFirebaseToken(String uid, String email, String name) throws Exception {
        FirebaseToken token = mock(FirebaseToken.class);
        when(token.getUid()).thenReturn(uid);
        when(token.getEmail()).thenReturn(email);
        when(token.getName()).thenReturn(name);
        return token;
    }

    @Nested
    @DisplayName("Normal Firebase Login (Không có tài khoản Khách)")
    class NormalLoginTests {

        @Test
        @DisplayName("Đăng nhập với tài khoản Firebase mới -> Tạo User mới thành công")
        void firebaseLogin_newFirebaseUser_createsUser() throws Exception {
            FirebaseToken token = mockFirebaseToken("fb-new", "new@example.com", "Nguyễn Văn A");
            when(firebaseAuth.verifyIdToken("valid-token")).thenReturn(token);
            when(userRepository.findByFirebaseUid("fb-new")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User u = invocation.getArgument(0);
                u.setId(100L);
                return u;
            });
            when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-token-123");

            FirebaseLoginRequest request = new FirebaseLoginRequest("valid-token");
            LoginResult result = authService.firebaseLogin(request, null);

            assertThat(result.accessToken()).isEqualTo("access-token-123");
            assertThat(result.tokenType()).isEqualTo("Bearer");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Đăng nhập với tài khoản Firebase đã có sẵn -> Trả về JWT của user đó")
        void firebaseLogin_existingFirebaseUser_returnsToken() throws Exception {
            FirebaseToken token = mockFirebaseToken("fb-exist", "exist@example.com", "User Đã Có");
            when(firebaseAuth.verifyIdToken("valid-token")).thenReturn(token);

            User existingUser = User.create("fb-exist", "exist@example.com", "User Đã Có", null, null);
            existingUser.setId(50L);
            when(userRepository.findByFirebaseUid("fb-exist")).thenReturn(Optional.of(existingUser));
            when(jwtService.generateAccessToken(existingUser)).thenReturn("access-token-50");

            FirebaseLoginRequest request = new FirebaseLoginRequest("valid-token");
            LoginResult result = authService.firebaseLogin(request, null);

            assertThat(result.accessToken()).isEqualTo("access-token-50");
            verify(userRepository, never()).save(any(User.class));
            verifyNoInteractions(roomMemberRepository, messageRepository);
        }

        @Test
        @DisplayName("Token Firebase không hợp lệ -> Ném lỗi INVALID_FIREBASE_TOKEN")
        void firebaseLogin_invalidToken_throwsException() throws Exception {
            FirebaseAuthException firebaseEx = mock(FirebaseAuthException.class);
            when(firebaseAuth.verifyIdToken("invalid-token"))
                    .thenThrow(firebaseEx);

            FirebaseLoginRequest request = new FirebaseLoginRequest("invalid-token");
            assertThatThrownBy(() -> authService.firebaseLogin(request, null))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.INVALID_FIREBASE_TOKEN));
        }

        @Test
        @DisplayName("Firebase không cung cấp email -> Ném lỗi FIREBASE_EMAIL_REQUIRED")
        void firebaseLogin_missingEmail_throwsException() throws Exception {
            FirebaseToken token = mockFirebaseToken("fb-no-email", null, "No Email");
            when(firebaseAuth.verifyIdToken("token-no-email")).thenReturn(token);

            FirebaseLoginRequest request = new FirebaseLoginRequest("token-no-email");
            assertThatThrownBy(() -> authService.firebaseLogin(request, null))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.FIREBASE_EMAIL_REQUIRED));
        }

        @Test
        @DisplayName("Email đã được sử dụng bởi tài khoản khác -> Ném lỗi EMAIL_ALREADY_EXISTS")
        void firebaseLogin_emailConflict_throwsException() throws Exception {
            FirebaseToken token = mockFirebaseToken("fb-new-uid", "conflict@example.com", "Tên Mới");
            when(firebaseAuth.verifyIdToken("token-conflict")).thenReturn(token);
            when(userRepository.findByFirebaseUid("fb-new-uid")).thenReturn(Optional.empty());

            User otherUser = User.create("fb-other-uid", "conflict@example.com", "Tên Cũ", null, null);
            otherUser.setId(99L);
            when(userRepository.findByEmail("conflict@example.com")).thenReturn(Optional.of(otherUser));

            FirebaseLoginRequest request = new FirebaseLoginRequest("token-conflict");
            assertThatThrownBy(() -> authService.firebaseLogin(request, null))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));
        }
    }

    @Nested
    @DisplayName("Giai đoạn 5 - Trường hợp 1: Nâng cấp tài khoản Khách thành User thật (Tài khoản Firebase mới)")
    class UpgradeGuestToNewUserTests {

        @Test
        @DisplayName("Khách cầm token đăng nhập Firebase mới -> Giữ nguyên ID, nâng cấp role USER và lưu thông tin Firebase")
        void case1_upgradeGuestDirectly_whenFirebaseUserIsNew() throws Exception {
            Long guestId = 10L;
            User guestUser = User.createGuest("Khách #1234");
            guestUser.setId(guestId);

            FirebaseToken token = mockFirebaseToken("fb-brand-new", "upgrade@example.com", "Nguyễn Khách Thật");
            when(firebaseAuth.verifyIdToken("fb-token-upgrade")).thenReturn(token);

            when(userRepository.findById(guestId)).thenReturn(Optional.of(guestUser));
            when(userRepository.findByFirebaseUid("fb-brand-new")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("upgrade@example.com")).thenReturn(Optional.empty());
            when(userRepository.save(guestUser)).thenReturn(guestUser);
            when(jwtService.generateAccessToken(guestUser)).thenReturn("new-user-jwt");

            FirebaseLoginRequest request = new FirebaseLoginRequest("fb-token-upgrade");
            LoginResult result = authService.firebaseLogin(request, guestId);

            assertThat(result.accessToken()).isEqualTo("new-user-jwt");
            // Kiểm tra guestUser đã được cập nhật thành USER thật
            assertThat(guestUser.getId()).isEqualTo(guestId); // ID được bảo toàn
            assertThat(guestUser.getRole()).isEqualTo(Role.USER);
            assertThat(guestUser.getFirebaseUid()).isEqualTo("fb-brand-new");
            assertThat(guestUser.getEmail()).isEqualTo("upgrade@example.com");
            assertThat(guestUser.getFullName()).isEqualTo("Nguyễn Khách Thật");

            verify(userRepository).save(guestUser);
            verify(userRepository, never()).delete(any(User.class));
        }

        @Test
        @DisplayName("Guest token được gửi trong request body (guestToken) -> Vẫn giải mã và nâng cấp thành công")
        void case1_upgradeGuest_whenGuestTokenProvidedInBody() throws Exception {
            Long guestId = 15L;
            User guestUser = User.createGuest("Khách #9999");
            guestUser.setId(guestId);

            Jwt decodedGuestJwt = mock(Jwt.class);
            when(decodedGuestJwt.getClaim("userId")).thenReturn(guestId);
            when(jwtDecoder.decode("guest-jwt-in-body")).thenReturn(decodedGuestJwt);

            FirebaseToken token = mockFirebaseToken("fb-uid-body", "body@example.com", "Người Dùng Body");
            when(firebaseAuth.verifyIdToken("fb-token")).thenReturn(token);

            when(userRepository.findById(guestId)).thenReturn(Optional.of(guestUser));
            when(userRepository.findByFirebaseUid("fb-uid-body")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("body@example.com")).thenReturn(Optional.empty());
            when(userRepository.save(guestUser)).thenReturn(guestUser);
            when(jwtService.generateAccessToken(guestUser)).thenReturn("body-jwt");

            FirebaseLoginRequest request = new FirebaseLoginRequest("fb-token", "guest-jwt-in-body");
            LoginResult result = authService.firebaseLogin(request, null);

            assertThat(result.accessToken()).isEqualTo("body-jwt");
            assertThat(guestUser.getRole()).isEqualTo(Role.USER);
            assertThat(guestUser.getFirebaseUid()).isEqualTo("fb-uid-body");
            verify(userRepository).save(guestUser);
        }

        @Test
        @DisplayName("Khách nâng cấp nhưng Firebase không có tên -> Giữ tên nickname của khách hoặc đổi thành người dùng mới")
        void case1_upgradeGuest_whenFirebaseNameIsBlank() throws Exception {
            Long guestId = 12L;
            User guestUser = User.createGuest("Biệt Danh Vui");
            guestUser.setId(guestId);

            FirebaseToken token = mockFirebaseToken("fb-noname", "noname@example.com", "   ");
            when(firebaseAuth.verifyIdToken("fb-token")).thenReturn(token);

            when(userRepository.findById(guestId)).thenReturn(Optional.of(guestUser));
            when(userRepository.findByFirebaseUid("fb-noname")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("noname@example.com")).thenReturn(Optional.empty());
            when(userRepository.save(guestUser)).thenReturn(guestUser);
            when(jwtService.generateAccessToken(guestUser)).thenReturn("jwt-res");

            FirebaseLoginRequest request = new FirebaseLoginRequest("fb-token");
            authService.firebaseLogin(request, guestId);

            assertThat(guestUser.getRole()).isEqualTo(Role.USER);
            assertThat(guestUser.getFullName()).isEqualTo("Biệt Danh Vui");
        }
    }

    @Nested
    @DisplayName("Giai đoạn 5 - Trường hợp 2: Di chuyển dữ liệu sang tài khoản Firebase đã có sẵn & xóa Guest")
    class MigrateGuestToExistingUserTests {

        @Test
        @DisplayName("Tài khoản Firebase đã có sẵn -> Di chuyển phòng, tin nhắn và xóa user khách")
        void case2_migrateGuestData_whenFirebaseUserAlreadyExists() throws Exception {
            Long guestId = 20L;
            User guestUser = User.createGuest("Khách Tạm");
            guestUser.setId(guestId);

            Long existingUserId = 5L;
            User existingUser = User.create("fb-existing-acc", "old@example.com", "Tài Khoản Đã Có", null, null);
            existingUser.setId(existingUserId);

            FirebaseToken token = mockFirebaseToken("fb-existing-acc", "old@example.com", "Tài Khoản Đã Có");
            when(firebaseAuth.verifyIdToken("fb-token-case2")).thenReturn(token);

            when(userRepository.findById(guestId)).thenReturn(Optional.of(guestUser));
            when(userRepository.findByFirebaseUid("fb-existing-acc")).thenReturn(Optional.of(existingUser));

            // Giả lập existingUser đã ở room 101, còn guest ở room 101 và room 202
            when(roomMemberRepository.findRoomIdsByUserId(existingUserId)).thenReturn(List.of(101L));
            when(jwtService.generateAccessToken(existingUser)).thenReturn("existing-jwt");

            FirebaseLoginRequest request = new FirebaseLoginRequest("fb-token-case2");
            LoginResult result = authService.firebaseLogin(request, guestId);

            assertThat(result.accessToken()).isEqualTo("existing-jwt");

            // 1. Kiểm tra xóa membership trùng ở room 101 của guest
            verify(roomMemberRepository).deleteByUserIdAndRoomIdIn(eq(guestId), eq(List.of(101L)));

            // 2. Chuyển các membership còn lại (room 202) sang existingUserId
            verify(roomMemberRepository).reassignUserId(guestId, existingUserId);

            // 3. Chuyển tin nhắn của guest sang existingUserId
            verify(messageRepository).reassignSenderId(guestId, existingUserId);

            // 4. Chuyển chat_rooms tạo bởi guest (nếu có) sang existingUserId
            verify(chatRoomRepository).reassignCreatedBy(guestId, existingUserId);

            // 5. Xóa tài khoản khách tạm
            verify(userRepository).delete(guestUser);
            verify(userRepository).flush();
        }

        @Test
        @DisplayName("Tài khoản Firebase đã có sẵn nhưng chưa tham gia phòng nào -> Không gọi deleteByUserIdAndRoomIdIn")
        void case2_migrateGuestData_whenTargetUserHasNoRooms() throws Exception {
            Long guestId = 25L;
            User guestUser = User.createGuest("Khách #25");
            guestUser.setId(guestId);

            Long existingUserId = 6L;
            User existingUser = User.create("fb-no-rooms", "noroom@example.com", "User Mới Chơi", null, null);
            existingUser.setId(existingUserId);

            FirebaseToken token = mockFirebaseToken("fb-no-rooms", "noroom@example.com", "User Mới Chơi");
            when(firebaseAuth.verifyIdToken("fb-token-noroom")).thenReturn(token);

            when(userRepository.findById(guestId)).thenReturn(Optional.of(guestUser));
            when(userRepository.findByFirebaseUid("fb-no-rooms")).thenReturn(Optional.of(existingUser));
            when(roomMemberRepository.findRoomIdsByUserId(existingUserId)).thenReturn(List.of());
            when(jwtService.generateAccessToken(existingUser)).thenReturn("jwt-6");

            FirebaseLoginRequest request = new FirebaseLoginRequest("fb-token-noroom");
            authService.firebaseLogin(request, guestId);

            verify(roomMemberRepository, never()).deleteByUserIdAndRoomIdIn(anyLong(), any());
            verify(roomMemberRepository).reassignUserId(guestId, existingUserId);
            verify(messageRepository).reassignSenderId(guestId, existingUserId);
            verify(userRepository).delete(guestUser);
        }

        @Test
        @DisplayName("Nếu currentUserId là USER thật (không phải GUEST) -> Không tiến hành migrate hay xóa user đó")
        void case2_doNotMigrate_whenCurrentUserIsNotGuest() throws Exception {
            Long currentRegisteredUserId = 30L;
            User registeredUser = User.create("fb-prev", "prev@example.com", "User Thật", null, null);
            registeredUser.setId(currentRegisteredUserId);

            Long targetUserId = 40L;
            User targetUser = User.create("fb-target", "target@example.com", "Target User", null, null);
            targetUser.setId(targetUserId);

            FirebaseToken token = mockFirebaseToken("fb-target", "target@example.com", "Target User");
            when(firebaseAuth.verifyIdToken("fb-token-other")).thenReturn(token);

            when(userRepository.findById(currentRegisteredUserId)).thenReturn(Optional.of(registeredUser));
            when(userRepository.findByFirebaseUid("fb-target")).thenReturn(Optional.of(targetUser));
            when(jwtService.generateAccessToken(targetUser)).thenReturn("jwt-target");

            FirebaseLoginRequest request = new FirebaseLoginRequest("fb-token-other");
            LoginResult result = authService.firebaseLogin(request, currentRegisteredUserId);

            assertThat(result.accessToken()).isEqualTo("jwt-target");
            // Không xóa registeredUser và không migrate
            verify(userRepository, never()).delete(any(User.class));
            verifyNoInteractions(roomMemberRepository, messageRepository);
        }
    }

    @Nested
    @DisplayName("Guest Login (Đăng nhập Khách)")
    class GuestLoginTests {

        @Test
        @DisplayName("Đăng nhập Khách không truyền nickname -> Tự tạo nickname dạng Khách #XXXX")
        void guestLogin_defaultNickname() {
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User u = invocation.getArgument(0);
                u.setId(77L);
                return u;
            });
            when(jwtService.generateAccessToken(any(User.class))).thenReturn("guest-jwt-token");

            LoginResult result = authService.guestLogin(null);

            assertThat(result.accessToken()).isEqualTo("guest-jwt-token");
            verify(userRepository).save(argThat(u -> u.isGuest() && u.getFullName().startsWith("Khách #")));
        }

        @Test
        @DisplayName("Đăng nhập Khách có truyền nickname -> Lưu đúng nickname đã chọn")
        void guestLogin_customNickname() {
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User u = invocation.getArgument(0);
                u.setId(88L);
                return u;
            });
            when(jwtService.generateAccessToken(any(User.class))).thenReturn("guest-jwt-custom");

            GuestLoginRequest request = new GuestLoginRequest("Hacker Mũ Trắng");
            LoginResult result = authService.guestLogin(request);

            assertThat(result.accessToken()).isEqualTo("guest-jwt-custom");
            verify(userRepository).save(argThat(u -> u.isGuest() && u.getFullName().equals("Hacker Mũ Trắng")));
        }
    }
}
