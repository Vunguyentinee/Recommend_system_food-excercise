package com.ptit.rc_system.controller;

import com.ptit.rc_system.entity.User;
import com.ptit.rc_system.repository.UserRepository;
import java.time.LocalDateTime;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository userRepository;

    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        if (isBlank(request.userName()) || isBlank(request.password()) || isBlank(request.email())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("Missing required fields"));
        }
        if (userRepository.findByUserName(request.userName()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("UserName already exists"));
        }
        User user = new User();
        user.setUserName(request.userName());
        user.setPassword(request.password());
        user.setEmail(request.email());
        user.setCreateAt(LocalDateTime.now());
        User saved = userRepository.save(user);
        return ResponseEntity.ok(new AuthResponse(saved.getUserId(), saved.getUserName(), resolveRole(saved)));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        if (isBlank(request.userName()) || isBlank(request.password())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("Missing required fields"));
        }
        return userRepository.findByUserName(request.userName())
            .filter(user -> user.getPassword().equals(request.password()))
            .<ResponseEntity<?>>map(user -> ResponseEntity.ok(
                new AuthResponse(user.getUserId(), user.getUserName(), resolveRole(user))
            ))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("Invalid credentials")));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<?> getUser(@PathVariable Long userId) {
        return userRepository.findById(userId)
            .<ResponseEntity<?>>map(user -> ResponseEntity.ok(
                new AuthResponse(user.getUserId(), user.getUserName(), resolveRole(user))
            ))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("User not found")));
    }

    private String resolveRole(User user) {
        return "admin".equalsIgnoreCase(user.getUserName()) ? "admin" : "user";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public record RegisterRequest(String userName, String password, String email) {
    }

    public record LoginRequest(String userName, String password) {
    }

    public record AuthResponse(Long userId, String userName, String role) {
    }

    public record ErrorResponse(String message) {
    }
}

