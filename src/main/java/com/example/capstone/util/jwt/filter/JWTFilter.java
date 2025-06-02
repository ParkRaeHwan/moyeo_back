package com.example.capstone.util.jwt.filter;

import com.example.capstone.user.exception.InvalidTokenException;
import com.example.capstone.user.exception.TokenExpiredException;
import com.example.capstone.util.oauth2.dto.CustomOAuth2User;
import com.example.capstone.util.oauth2.dto.OAuth2DTO;
import com.example.capstone.util.jwt.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class JWTFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // 해당 경로 검증 X
        String requestURI = request.getRequestURI();
        if (requestURI.startsWith("/oauth2/") || requestURI.startsWith("/auth/reissue") || requestURI.startsWith("/auth/logout")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = parseJwt(request);
        CustomOAuth2User customOAuth2User;
        
        // 임시 토큰 검증 (회원 가입 시)
        try {
            if (requestURI.startsWith("/auth/signup")) {
                if (token == null || !jwtUtil.validateJwt(token) || !"TEMP".equals(jwtUtil.getTypeFromJwt(token))) {
                    sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid temporary token");
                    return;
                }
                customOAuth2User = new CustomOAuth2User(OAuth2DTO.builder()
                        .providerId(jwtUtil.getProviderIdFromJwt(token))
                        .email(jwtUtil.getEmailFromJwt(token))
                        .build());
            } else {
                // 정식 토큰 발급
                if (token == null || !jwtUtil.validateJwt(token) || !"ACCESS".equals(jwtUtil.getTypeFromJwt(token))) {
                    sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid access token");
                    return;
                }
                customOAuth2User = new CustomOAuth2User(OAuth2DTO.builder()
                        .providerId(jwtUtil.getProviderIdFromJwt(token))
                        .email(jwtUtil.getEmailFromJwt(token))
                        .nickname(jwtUtil.getNicknameFromJwt(token))
                        .build());
            }

            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    customOAuth2User, null, customOAuth2User.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
            
        } catch (TokenExpiredException e) {
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Token has expired");
        } catch (InvalidTokenException e) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid token");
        }
    }

    private void sendErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(status);
        response.getWriter().write("{\"error\": \"" + message + "\"}");
    }

    private String parseJwt(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }
}
