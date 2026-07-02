package com.ptit.rc_system.security;

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

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtRequestFilter jwtRequestFilter;

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
                // Cho phép truy cập tự do vào các API đăng ký/đăng nhập
                .requestMatchers("/api/auth/**").permitAll()
                // Cho phép truy cập tự do vào API sức khỏe của AI Service
                .requestMatchers("/api/ai/health").permitAll()
                
                // MẸO BẢO MẬT & TƯƠNG THÍCH:
                // Để không làm break Frontend hiện tại (nếu chưa truyền JWT Header),
                // chúng ta tạm thời permitAll() cho toàn bộ API, nhưng Filter vẫn đọc và giải mã JWT nếu có.
                //
                // Khi Frontend của bạn đã sẵn sàng truyền JWT Header (Bearer token), hãy thay đổi dòng dưới đây:
                // từ .anyRequest().permitAll() sang .anyRequest().authenticated()
                .anyRequest().permitAll()
            )
            // Cấu hình session stateless
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Thêm JwtRequestFilter vào trước UsernamePasswordAuthenticationFilter
            .addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Cấu hình cho phép các domain kết nối (origins)
        configuration.setAllowedOriginPatterns(List.of("*"));
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
