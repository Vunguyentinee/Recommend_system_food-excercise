package com.ptit.rc_system.controller;

import com.ptit.rc_system.entity.User;
import com.ptit.rc_system.repository.UserRepository;
import com.ptit.rc_system.security.JwtTokenUtil;
import com.ptit.rc_system.security.CustomUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenUtil jwtTokenUtil;
    private final CustomUserDetailsService userDetailsService;

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtTokenUtil jwtTokenUtil,
                          CustomUserDetailsService userDetailsService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenUtil = jwtTokenUtil;
        this.userDetailsService = userDetailsService;
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
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setEmail(request.email());
        user.setCreateAt(LocalDateTime.now());
        User saved = userRepository.save(user);

        String role = resolveRole(saved);
        UserDetails userDetails = userDetailsService.loadUserByUsername(saved.getUserName());
        String token = jwtTokenUtil.generateToken(userDetails, saved.getUserId(), role);

        return ResponseEntity.ok(new AuthResponse(saved.getUserId(), saved.getUserName(), role, token));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        if (isBlank(request.userName()) || isBlank(request.password())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("Missing required fields"));
        }
        Optional<User> optUser = userRepository.findByUserName(request.userName());
        if (optUser.isPresent() && passwordEncoder.matches(request.password(), optUser.get().getPassword())) {
            User user = optUser.get();
            String role = resolveRole(user);
            UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUserName());
            String token = jwtTokenUtil.generateToken(userDetails, user.getUserId(), role);
            return ResponseEntity.ok(new AuthResponse(user.getUserId(), user.getUserName(), role, token));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("Invalid credentials"));
        }
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<?> getUser(@PathVariable Long userId) {
        return userRepository.findById(userId)
            .<ResponseEntity<?>>map(user -> ResponseEntity.ok(
                new AuthResponse(user.getUserId(), user.getUserName(), resolveRole(user), null)
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

    public record AuthResponse(Long userId, String userName, String role, String token) {
    }

    public record ErrorResponse(String message) {
    }
}
