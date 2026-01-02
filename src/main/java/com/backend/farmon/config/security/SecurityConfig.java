package com.backend.farmon.config.security;

import lombok.RequiredArgsConstructor;
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

@RequiredArgsConstructor
@EnableWebSecurity // Spring Security 설정 활성화
@Configuration
public class SecurityConfig { // 애플리케이션의 보안 정책을 정의
    private CustomUserDetailsService userDetailsService;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint; // Unauthorized 핸들러
    private final CustomAccessDeniedHandler customAccessDeniedHandler; // Forbidden 핸들러

    // SecurityFilterChain 정의
    // 추후에 농업인 관련 페이지는 농업인만, 전문가 관련 페이지는 전문가만 가입할 수 있도록 수정 필요
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf((auth) -> auth.disable()) // 필요 시 CSRF 보호 비활성화
                .formLogin((auth) -> auth.disable()) // form 로그인 방식 disable
                .httpBasic((auth) -> auth.disable()) // http Basic 인증 방식 disable
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 세션 STATELESS 상태로 설정
                .authorizeHttpRequests((auth) -> auth
//                        // FARMER 역할만 접근 가능
//                        .requestMatchers("/farmer/**").hasRole("FARMER")
//
//                        // EXPERT 역할만 접근 가능
//                        .requestMatchers("/expert/**").hasRole("EXPERT")
//
//                        // 인증된 사용자(로그인한 사용자)만 접근 가능
//                        .requestMatchers("/").authenticated()

                        // 공용 접근 허용 (누구나 접근 가능)
                        .requestMatchers("/api/login","/api/generate","/api/verify","/api/user/join", "/api/user/find-email", "/api/user/reset-password",
                                "/api/home/community", "/api/home/popular",
                                "/api/expert/list", "/api/expert/{expert-id}",
                                "/api/posts/popular/list/**", "/api/posts/all/list/**", "/api/posts/free/list/**","/api/posts/qna/list/**",
                                "/api/posts/expertCol/list/**", "/api/posts/{postId}/comments",
                                "/ws-stomp/**", "/test", "/actuator/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll() // Swagger 경로 허용
                        .anyRequest().authenticated()
                )

                // Unauthorized, Forbidden 에러 핸들러 추가
                .exceptionHandling(exception -> exception.authenticationEntryPoint(authenticationEntryPoint))
                .exceptionHandling(exception -> exception.accessDeniedHandler(customAccessDeniedHandler))

                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);  // JWT 필터 추가

        return http.build();
    }

    // 비밀번호 암호화
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 커스텀 필터에 주입할 AuthenticationManager Bean 등록
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public JWTAuthenticationFilter jwtAuthenticationFilter() {
        return new JWTAuthenticationFilter();
    }
}
