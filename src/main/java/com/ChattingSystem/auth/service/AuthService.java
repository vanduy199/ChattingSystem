package com.ChattingSystem.auth.service;

import com.ChattingSystem.auth.dto.request.FirebaseLoginRequest;
import com.ChattingSystem.auth.dto.response.LoginResult;
import com.ChattingSystem.auth.dto.response.MeResponse;

public interface AuthService {
    LoginResult firebaseLogin(FirebaseLoginRequest request);

    MeResponse getCurrentUser(String firebaseUid);
}
