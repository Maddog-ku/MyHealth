package com.myhealth.user;

import com.myhealth.auth.AuthDtos.ProfileResponse;
import com.myhealth.auth.AuthDtos.UserResponse;
import com.myhealth.auth.CurrentUser;
import com.myhealth.user.UserDtos.ProfileUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class UserController {
    private final CurrentUser currentUser;
    private final UserService userService;

    public UserController(CurrentUser currentUser, UserService userService) {
        this.currentUser = currentUser;
        this.userService = userService;
    }

    @GetMapping
    UserResponse me() {
        return userService.me(currentUser.require());
    }

    @PutMapping("/profile")
    ProfileResponse updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        return userService.updateProfile(currentUser.require(), request);
    }

    @DeleteMapping
    ResponseEntity<Void> deleteAccount() {
        userService.deleteAccount(currentUser.require());
        return ResponseEntity.noContent().build();
    }
}
