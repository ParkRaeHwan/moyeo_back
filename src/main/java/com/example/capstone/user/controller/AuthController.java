package com.example.capstone.user.controller;

import com.example.capstone.user.dto.SignupResDto;
import com.example.capstone.user.service.AuthService;
import com.example.capstone.util.oauth2.dto.CustomOAuth2User;
import com.example.capstone.user.dto.UserProfileReqDto;
import com.example.capstone.user.entity.UserEntity;
import com.example.capstone.user.service.UserService;
import com.example.capstone.util.jwt.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final AuthService authService;

    @Operation(summary = "회원가입 API",
            description = "닉네임, 나이, 성별, MBTI, 이미지 입력")
    @PostMapping(value = "/signup", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> signup(
            @AuthenticationPrincipal CustomOAuth2User userDetails,
            @Valid @RequestPart("userInfo") UserProfileReqDto profileRequestDto,
            @RequestPart(value = "profileImage", required = false) MultipartFile profileImage) {
        SignupResDto signupResDto = userService.signup(userDetails, profileRequestDto, profileImage);
        return new ResponseEntity<>(signupResDto, HttpStatus.OK);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("refreshToken") String refreshToken) {
        authService.logout(refreshToken);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("/reissue")
    public ResponseEntity<?> reissue(@RequestHeader("refreshToken") String refreshToken) {
        SignupResDto signupResDto = authService.reissue(refreshToken);
        return new ResponseEntity<>(signupResDto, HttpStatus.OK);
    }
}
