package com.ChattingSystem.auth.service;

import com.ChattingSystem.auth.dto.request.FirebaseLoginRequest;
import com.ChattingSystem.auth.dto.response.LoginResult;
import com.ChattingSystem.shared.response.ApiResponse;

public interface AuthService {
    LoginResult firebaseLogin(FirebaseLoginRequest request);
}
