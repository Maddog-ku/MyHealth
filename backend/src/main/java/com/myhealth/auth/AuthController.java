package com.myhealth.auth;

import com.myhealth.auth.AuthDtos.LoginRequest;
import com.myhealth.auth.AuthDtos.LogoutRequest;
import com.myhealth.auth.AuthDtos.RefreshRequest;
import com.myhealth.auth.AuthDtos.RegisterRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final AuthRateLimiter rateLimiter;

    public AuthController(AuthService authService, AuthRateLimiter rateLimiter) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/register")
    ResponseEntity<AuthDtos.UserResponse> register(@Valid @RequestBody RegisterRequest request,
                                                   HttpServletRequest servletRequest) {
        rateLimiter.checkRegister(servletRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    AuthDtos.AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        rateLimiter.checkLogin(servletRequest, request.email());
        return authService.login(request);
    }

    @PostMapping("/refresh")
    AuthDtos.AuthResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest servletRequest) {
        rateLimiter.checkRefresh(servletRequest);
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
