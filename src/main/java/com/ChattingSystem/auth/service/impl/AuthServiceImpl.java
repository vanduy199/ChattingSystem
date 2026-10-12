package com.ChattingSystem.auth.service.impl;

import com.ChattingSystem.auth.dto.request.FirebaseLoginRequest;
import com.ChattingSystem.auth.dto.response.LoginResult;
import com.ChattingSystem.auth.service.AuthService;
import com.ChattingSystem.security.jwt.JwtService;
import com.ChattingSystem.shared.exeption.AppException;
import com.ChattingSystem.shared.exeption.ErrorCode;
import com.ChattingSystem.shared.properties.JwtProperties;
import com.ChattingSystem.user.entity.User;
import com.ChattingSystem.user.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private final FirebaseAuth firebaseAuth;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    public AuthServiceImpl(FirebaseAuth firebaseAuth, UserRepository userRepository,
                           JwtService jwtService, JwtProperties jwtProperties) {
        this.firebaseAuth = firebaseAuth;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    @Override
    @Transactional
    public LoginResult firebaseLogin(FirebaseLoginRequest request) {
        FirebaseToken decodedToken;
        try {
            decodedToken = firebaseAuth.verifyIdToken(request.idToken());
        } catch (com.google.firebase.auth.FirebaseAuthException exception) {
            throw new AppException(ErrorCode.INVALID_FIREBASE_TOKEN);
        }
        String uid = decodedToken.getUid();
        String email = decodedToken.getEmail();
        String fullName = decodedToken.getName();

        if(email==null || email.isEmpty()){
            throw new AppException(ErrorCode.FIREBASE_EMAIL_REQUIRED);
        }
        User user = userRepository.findByFirebaseUid(uid)
                .orElse(null);
        if (user == null) {

            if (userRepository.findByEmail(email).isPresent()) {
                throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
            }

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
        String accessToken = jwtService.generateAccessToken(user);
        long expiresInSeconds =
                jwtProperties.accessTokenExpiration().toSeconds();
        return LoginResult.from(
                accessToken,
                "Bearer",
                expiresInSeconds
        );
    }
}
