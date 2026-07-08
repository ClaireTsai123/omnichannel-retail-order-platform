package com.ordering.userservice.controller;
import com.ordering.common.dto.ApiResponse;
import com.ordering.common.dto.UserDTO;
import com.ordering.userservice.dto.AuthResponse;
import com.ordering.userservice.dto.LoginRequest;
import com.ordering.userservice.dto.LogoutRequest;
import com.ordering.userservice.dto.RefreshTokenRequest;
import com.ordering.userservice.dto.RegisterRequest;
import com.ordering.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;
    
    @PostMapping("/register")
    public ApiResponse<UserDTO> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(userService.register(request));
    }
    
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(userService.login(request.getUsername(), request.getPassword()));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(userService.refresh(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<?> logout(@Valid @RequestBody LogoutRequest request) {
        userService.logout(request.getRefreshToken());
        return ApiResponse.success(null);
    }

}
