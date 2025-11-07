package com.example.capstone.util.jwt;

import com.example.capstone.user.exception.InvalidTokenException;
import com.example.capstone.user.exception.TokenExpiredException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String jwtSecret;
    @Value("${jwt.access.expirationMs}")
    private long jwtAccessExpirationMs;
    @Value("${jwt.refresh.expiationMs}")
    private long jwtRefreshExpirationMs;

    @Value("${jwt.temp.expirationMs}")
    private long jwtTempExpirationMs;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtSecret));
    }

    public String generateToken(String type, String providerId, String email, String nickname) {
        long expirationMs = type.equals("ACCESS") ? jwtAccessExpirationMs : jwtRefreshExpirationMs;
        return Jwts.builder()
                .setSubject(providerId)
                .claim("email", email) // Todo: email 데이터 필요한지 확인
                .claim("nickname", nickname)
                .claim("type", type)
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() + expirationMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateTempToken(String providerId, String email) {
        return Jwts.builder()
                .setSubject(providerId)
                .claim("email", email)
                .claim("type", "TEMP")
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() + jwtTempExpirationMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();

    }

    public String getProviderIdFromJwt(String token) {
        return Jwts.parser()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    public String getEmailFromJwt(String token) {
        return Jwts.parser()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get("email", String.class);
    }

    public String getNicknameFromJwt(String token) {
        return Jwts.parser()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get("nickname", String.class);
    }

    public String getTypeFromJwt(String token) {
        return Jwts.parser()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get("type", String.class);
    }

    public boolean validateJwt(String authToken) {
        try {
            Jwts.parser()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(authToken);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            throw new InvalidTokenException("Invalid or missing token");
        } catch (ExpiredJwtException e) {
            throw new TokenExpiredException("JWT token is expired");
        } catch (UnsupportedJwtException e) {
            throw new InvalidTokenException("JWT token is unsupported");
        } catch (IllegalArgumentException e) {
            throw new InvalidTokenException("JWT claims string is empty");
        }
    }
}
