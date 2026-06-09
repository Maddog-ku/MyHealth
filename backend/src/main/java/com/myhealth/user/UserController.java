package com.myhealth.user;

import com.myhealth.auth.AuthDtos.ChangePasswordRequest;
import com.myhealth.auth.AuthDtos.ProfileResponse;
import com.myhealth.auth.AuthDtos.UserResponse;
import com.myhealth.auth.AuthService;
import com.myhealth.auth.CurrentUser;
import com.myhealth.user.SessionDtos.RevokeOthersRequest;
import com.myhealth.user.SessionDtos.SessionListResponse;
import com.myhealth.user.UserDtos.ProfileUpdateRequest;
import com.myhealth.user.UserDtos.UpdateAccountRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class UserController {
    private final CurrentUser currentUser;
    private final UserService userService;
    private final SessionService sessionService;
    private final AuthService authService;

    public UserController(CurrentUser currentUser, UserService userService,
                         SessionService sessionService, AuthService authService) {
        this.currentUser = currentUser;
        this.userService = userService;
        this.sessionService = sessionService;
        this.authService = authService;
    }

    @GetMapping
    UserResponse me() {
        return userService.me(currentUser.require());
    }

    @PutMapping("/account")
    UserResponse updateAccount(@Valid @RequestBody UpdateAccountRequest request) {
        return userService.updateAccount(currentUser.require(), request.name());
    }

    @PutMapping("/profile")
    ProfileResponse updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        return userService.updateProfile(currentUser.require(), request);
    }

    @PostMapping("/password")
    ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @RequestHeader(value = "X-Refresh-Token", required = false) String currentRefreshToken) {
        authService.changePassword(currentUser.require(), request.currentPassword(),
                request.newPassword(), currentRefreshToken);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions")
    SessionListResponse sessions(
            @RequestHeader(value = "X-Refresh-Token", required = false) String currentRefreshToken) {
        return sessionService.list(currentUser.require(), currentRefreshToken);
    }

    @DeleteMapping("/sessions/{id}")
    ResponseEntity<Void> revokeSession(@PathVariable Long id) {
        sessionService.revoke(currentUser.require(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions/revoke-others")
    SessionListResponse revokeOtherSessions(@Valid @RequestBody RevokeOthersRequest request) {
        return sessionService.revokeOthers(currentUser.require(), request.refreshToken());
    }

    @DeleteMapping
    ResponseEntity<Void> deleteAccount() {
        userService.deleteAccount(currentUser.require());
        return ResponseEntity.noContent().build();
    }
}
