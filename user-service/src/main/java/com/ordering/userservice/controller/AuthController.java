package com.ordering.userservice.controller;
import com.ordering.common.dto.ApiResponse;
import com.ordering.common.dto.UserDTO;
import com.ordering.userservice.dto.LoginRequest;
import com.ordering.userservice.dto.RegisterRequest;
import com.ordering.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
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
    public ApiResponse<Map<String, String>> login(@RequestBody LoginRequest request) {
        String token = userService.login(request.getUsername(), request.getPassword());
        return ApiResponse.success(Map.of("token", token));
    }

}
