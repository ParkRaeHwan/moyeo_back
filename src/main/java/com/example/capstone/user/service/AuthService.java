package com.example.capstone.user.service;

import com.example.capstone.user.dto.SignupResDto;
import com.example.capstone.user.exception.InvalidTokenException;
import com.example.capstone.util.jwt.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${jwt.refresh.expiationMs}")
    private long refreshExpiationMs;

    public SignupResDto reissue(String refreshToken) {
        if (!jwtUtil.validateJwt(refreshToken) || !jwtUtil.getTypeFromJwt(refreshToken).equals("REFRESH")) {
            throw new InvalidTokenException("Invalid refresh token");
        }

        String nickname = jwtUtil.getNicknameFromJwt(refreshToken);
        String providerId = jwtUtil.getProviderIdFromJwt(refreshToken);
        String email = jwtUtil.getEmailFromJwt(refreshToken);

        // Todo: Redis 통한 Refresh 토큰 유효성 검증 후 삭제
        String storedRefreshToken = redisTemplate.opsForValue().get("REFRESH:" + nickname);
        if (storedRefreshToken == null || !storedRefreshToken.equals(refreshToken)) {
            throw new InvalidTokenException("Invalid refresh token");
        }

        redisTemplate.delete("REFRESH:" + nickname);

        // Todo: 새 Refresh 토큰 저장
        String newAccessToken = jwtUtil.generateToken("ACCESS", providerId, email, nickname);
        String newRefreshToken = jwtUtil.generateToken("REFRESH", providerId, email, nickname);

        redisTemplate.opsForValue().set("REFRESH:" + nickname, newRefreshToken, refreshExpiationMs, TimeUnit.MILLISECONDS);

        return SignupResDto.builder()
                .refreshToken(newRefreshToken)
                .accessToken(newAccessToken)
                .build();
    }

    public void logout(String refreshToken) {
        if (!jwtUtil.validateJwt(refreshToken) || !jwtUtil.getTypeFromJwt(refreshToken).equals("REFRESH")) {
            throw new InvalidTokenException("Invalid refresh token");
        }
        String nickname = jwtUtil.getNicknameFromJwt(refreshToken);

        // Todo: Redis 통한 Refresh 토큰 유효성 검증
        String storedRefreshToken = redisTemplate.opsForValue().get("REFRESH:" + nickname);
        if (storedRefreshToken == null || !storedRefreshToken.equals(refreshToken)) {
            throw new InvalidTokenException("Invalid refresh token");
        }

        redisTemplate.delete("REFRESH:" + nickname);
    }
}
