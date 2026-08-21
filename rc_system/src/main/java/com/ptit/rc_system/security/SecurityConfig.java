package com.ptit.rc_system.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtRequestFilter jwtRequestFilter;

    @Value("${app.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*,https://rc-system-health-backend.onrender.com}")
    private List<String> allowedOriginPatterns;

    public SecurityConfig(JwtRequestFilter jwtRequestFilter) {
        this.jwtRequestFilter = jwtRequestFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Tắt CSRF (vì chúng ta dùng JWT stateless)
            .csrf(csrf -> csrf.disable())
            // Kích hoạt cấu hình CORS cấu hình dưới đây
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // Cấu hình các API được phép truy cập
            .authorizeHttpRequests(auth -> auth
                // Chỉ đăng ký/đăng nhập được truy cập tự do; các API /api/auth/** khác
                // (vd. /api/auth/users/{id}) bắt buộc phải xác thực
                .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                // Cho phép truy cập tự do vào API sức khỏe của AI Service
                .requestMatchers("/api/ai/health").permitAll()
                // Cho phép truy cập tự do vào các tài nguyên tĩnh (frontend SPA)
                .requestMatchers("/", "/*.html", "/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                // Chỉ ADMIN mới được quản trị dữ liệu món ăn
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // Endpoint debug lộ toàn bộ ma trận tương tác người dùng -> chỉ ADMIN
                .requestMatchers("/api/debug/**").hasRole("ADMIN")
                // Mọi API còn lại bắt buộc phải xác thực bằng JWT
                .anyRequest().authenticated()
            )
            // Cấu hình session stateless
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Trả JSON 403 gọn gàng khi bị chặn bởi OwnershipGuard hoặc hasRole(...)
            .exceptionHandling(ex -> ex.accessDeniedHandler((request, response, accessDeniedException) -> {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Access denied\"}");
            }))
            // Thêm JwtRequestFilter vào trước UsernamePasswordAuthenticationFilter
            .addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Cấu hình cho phép các domain kết nối (origins) - chỉ các origin đã biết, không dùng wildcard
        configuration.setAllowedOriginPatterns(allowedOriginPatterns);
        // Cấu hình các HTTP Method được phép
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        // Cấu hình các Header được chấp nhận
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Cache-Control"));
        // Cho phép gửi credentials (cookie, authorization headers)
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
