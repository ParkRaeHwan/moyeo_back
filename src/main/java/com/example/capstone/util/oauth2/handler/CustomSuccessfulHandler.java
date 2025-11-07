package com.example.capstone.util.oauth2.handler;

import com.example.capstone.util.oauth2.dto.CustomOAuth2User;
import com.example.capstone.util.jwt.JwtUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class CustomSuccessfulHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;
    @Value("${jwt.refresh.expiationMs}")
    private long refreshExpiationMs;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();

        // 세션에서 redirect_uri 가져오기
        String redirectUri = (String) request.getSession().getAttribute("redirect_uri");
        if (redirectUri == null) {
            redirectUri = "exp://default-url"; // fallback 주소
        }

        // 신규, 기존 사용자 구분해 redirect
        if (oAuth2User.getTempToken() != null) {
            log.info("temp {}", oAuth2User.getTempToken());
            response.sendRedirect(redirectUri + "?mode=register&token=" + oAuth2User.getTempToken());
        } else {
            String accessToken = jwtUtil.generateToken("ACCESS", oAuth2User.getProviderId(), oAuth2User.getEmail(), oAuth2User.getNickname());
            String refreshToken = jwtUtil.generateToken("REFRESH", oAuth2User.getProviderId(), oAuth2User.getEmail(), oAuth2User.getNickname());
            log.info("access {}, refresh {}", accessToken, refreshToken);

            // refreshToken Redis 저장
            redisTemplate.opsForValue().set("REFRESH:%s".formatted(oAuth2User.getNickname()), refreshToken, refreshExpiationMs, TimeUnit.MILLISECONDS);

            String redirectUrl = redirectUri + "?mode=login&access=" + accessToken + "&refresh=" + refreshToken;
            response.sendRedirect(redirectUrl);
        }
    }
}
