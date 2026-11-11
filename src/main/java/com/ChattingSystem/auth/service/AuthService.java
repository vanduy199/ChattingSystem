package com.ChattingSystem.auth.service;

import com.ChattingSystem.auth.dto.request.FirebaseLoginRequest;
import com.ChattingSystem.auth.dto.request.GuestLoginRequest;
import com.ChattingSystem.auth.dto.response.LoginResult;
import com.ChattingSystem.auth.dto.response.MeResponse;

public interface AuthService {
    LoginResult firebaseLogin(FirebaseLoginRequest request);

    LoginResult firebaseLogin(FirebaseLoginRequest request, Long currentUserId);

    LoginResult guestLogin(GuestLoginRequest request);

    MeResponse getCurrentUser(String firebaseUid);

    MeResponse getCurrentUser(Long userId);
}
