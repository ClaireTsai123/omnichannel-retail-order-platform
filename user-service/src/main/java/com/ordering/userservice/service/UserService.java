package com.ordering.userservice.service;

import com.ordering.common.dto.UserDTO;
import com.ordering.common.exception.ForbiddenException;
import com.ordering.common.exception.ResourceNotFoundException;
import com.ordering.userservice.dto.RegisterRequest;
import com.ordering.userservice.entity.User;
import com.ordering.userservice.util.JwtUtil;
import com.ordering.userservice.util.Role;
import com.ordering.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService  {
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;


    public UserDTO register(RegisterRequest request) {
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        Role role = Role.ROLE_CUSTOMER;
        if ("VENDOR".equalsIgnoreCase(request.getRole())) {
            role = Role.ROLE_VENDOR;
        }
        user.setRole(role);
        return convertToDTO(userRepository.save(user));
    }

    public String login(String username, String password) {
       try {
           Authentication authentication = new UsernamePasswordAuthenticationToken(username, password);
           Authentication authenticated = authenticationManager.authenticate(authentication);
           User user = (User) authenticated.getPrincipal();

           return jwtUtil.generateToken(user);
       }catch (BadCredentialsException ex) {
           throw new ForbiddenException("Invalid username or password");
       }
    }

    public UserDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return convertToDTO(user);
    }

    public UserDTO updatedUser(Long id, UserDTO toUpdated) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        UserDTO userDTO = convertToDTO(user);
        userDTO.setId(toUpdated.getId());
        userDTO.setEmail(toUpdated.getEmail());
        userDTO.setRole(toUpdated.getRole());
        userDTO.setUsername(toUpdated.getUsername());
        userDTO.setPhone(toUpdated.getPhone());
        return userDTO;
    }

    public List<UserDTO> getAllUsers() {
        List<User> users = userRepository.findAll();
        return users.stream().map(this::convertToDTO).toList();
    }

    private UserDTO convertToDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setRole(user.getRole().name());
        return dto;
    }


    public void deleteUser(Long userId) {
        userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        userRepository.deleteById(userId);
    }


}
