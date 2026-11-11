package com.ChattingSystem.auth.service.impl;

import com.ChattingSystem.auth.dto.request.FirebaseLoginRequest;
import com.ChattingSystem.auth.dto.request.GuestLoginRequest;
import com.ChattingSystem.auth.dto.response.LoginResult;
import com.ChattingSystem.auth.dto.response.MeResponse;
import com.ChattingSystem.auth.service.AuthService;
import com.ChattingSystem.chatroom.repository.ChatRoomRepository;
import com.ChattingSystem.chatroom.repository.RoomMemberRepository;
import com.ChattingSystem.message.repository.MessageRepository;
import com.ChattingSystem.security.jwt.JwtService;
import com.ChattingSystem.shared.exeption.AppException;
import com.ChattingSystem.shared.exeption.ErrorCode;
import com.ChattingSystem.shared.properties.JwtProperties;
import com.ChattingSystem.user.entity.User;
import com.ChattingSystem.user.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AuthServiceImpl implements AuthService {

    private final FirebaseAuth firebaseAuth;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final JwtDecoder jwtDecoder;
    private final RoomMemberRepository roomMemberRepository;
    private final MessageRepository messageRepository;
    private final ChatRoomRepository chatRoomRepository;

    public AuthServiceImpl(FirebaseAuth firebaseAuth,
                           UserRepository userRepository,
                           JwtService jwtService,
                           JwtProperties jwtProperties,
                           JwtDecoder jwtDecoder,
                           RoomMemberRepository roomMemberRepository,
                           MessageRepository messageRepository,
                           ChatRoomRepository chatRoomRepository) {
        this.firebaseAuth = firebaseAuth;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.jwtDecoder = jwtDecoder;
        this.roomMemberRepository = roomMemberRepository;
        this.messageRepository = messageRepository;
        this.chatRoomRepository = chatRoomRepository;
    }

    @Override
    @Transactional
    public LoginResult firebaseLogin(FirebaseLoginRequest request) {
        return firebaseLogin(request, null);
    }

    @Override
    @Transactional
    public LoginResult firebaseLogin(FirebaseLoginRequest request, Long currentUserId) {
        Long guestUserId = resolveGuestUserId(currentUserId, request.guestToken());

        FirebaseToken decodedToken;
        try {
            decodedToken = firebaseAuth.verifyIdToken(request.idToken());
        } catch (com.google.firebase.auth.FirebaseAuthException exception) {
            throw new AppException(ErrorCode.INVALID_FIREBASE_TOKEN);
        }
        String uid = decodedToken.getUid();
        String email = decodedToken.getEmail();
        String fullName = decodedToken.getName();

        if (email == null || email.isEmpty()) {
            throw new AppException(ErrorCode.FIREBASE_EMAIL_REQUIRED);
        }

        User user = userRepository.findByFirebaseUid(uid).orElse(null);

        User guestUser = null;
        if (guestUserId != null) {
            guestUser = userRepository.findById(guestUserId)
                    .filter(User::isGuest)
                    .orElse(null);
        }

        if (user == null) {
            Optional<User> existingEmailUser = userRepository.findByEmail(email);
            if (existingEmailUser.isPresent()) {
                if (guestUser == null || !existingEmailUser.get().getId().equals(guestUser.getId())) {
                    throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
                }
            }

            if (guestUser != null) {
                // Trường hợp 1: Tài khoản Firebase mới -> Nâng cấp tài khoản Khách thành USER
                guestUser.upgradeToUser(uid, email, fullName);
                user = userRepository.save(guestUser);
            } else {
                if (fullName == null || fullName.isBlank()) {
                    fullName = "người dùng mới";
                }

                User newUser = User.create(
                        uid,
                        email,
                        fullName,
                        null,
                        null
                );

                user = userRepository.save(newUser);
            }
        } else {
            // Trường hợp 2: Tài khoản Firebase đã có sẵn -> Di chuyển dữ liệu từ guest sang user thật
            if (guestUser != null && !guestUser.getId().equals(user.getId())) {
                Long gId = guestUser.getId();
                Long targetId = user.getId();

                List<Long> targetRoomIds = roomMemberRepository.findRoomIdsByUserId(targetId);
                if (!targetRoomIds.isEmpty()) {
                    roomMemberRepository.deleteByUserIdAndRoomIdIn(gId, targetRoomIds);
                }
                roomMemberRepository.reassignUserId(gId, targetId);
                messageRepository.reassignSenderId(gId, targetId);
                chatRoomRepository.reassignCreatedBy(gId, targetId);

                userRepository.delete(guestUser);
                userRepository.flush();
            }
        }

        String accessToken = jwtService.generateAccessToken(user);
        long expiresInSeconds = jwtProperties.accessTokenExpiration().toSeconds();
        return LoginResult.from(
                accessToken,
                "Bearer",
                expiresInSeconds
        );
    }

    private Long resolveGuestUserId(Long currentUserId, String guestToken) {
        if (currentUserId != null) {
            return currentUserId;
        }
        if (guestToken != null && !guestToken.isBlank()) {
            try {
                Jwt decoded = jwtDecoder.decode(guestToken.trim());
                Object claim = decoded.getClaim("userId");
                if (claim instanceof Number number) {
                    return number.longValue();
                }
            } catch (Exception ignored) {
                // Token không hợp lệ hoặc đã hết hạn -> bỏ qua, coi như đăng nhập bình thường
            }
        }
        return null;
    }

    @Override
    @Transactional
    public LoginResult guestLogin(GuestLoginRequest request) {
        String nickname;
        if (request != null && request.nickname() != null && !request.nickname().isBlank()) {
            nickname = request.nickname().trim();
        } else {
            int randomSuffix = ThreadLocalRandom.current().nextInt(1000, 10000);
            nickname = "Khách #" + randomSuffix;
        }

        User guest = User.createGuest(nickname);
        User saved = userRepository.save(guest);

        String accessToken = jwtService.generateAccessToken(saved);
        long expiresInSeconds = jwtProperties.accessTokenExpiration().toSeconds();
        return LoginResult.from(
                accessToken,
                "Bearer",
                expiresInSeconds
        );
    }

    @Override
    @Transactional(readOnly = true)
    public MeResponse getCurrentUser(String firebaseUid) {
        User user = userRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return MeResponse.from(user);
    }

    @Override
    @Transactional(readOnly = true)
    public MeResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return MeResponse.from(user);
    }
}
