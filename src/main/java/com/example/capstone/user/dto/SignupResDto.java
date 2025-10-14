package com.example.capstone.user.dto;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class SignupResDto {
    private String accessToken;
    private String refreshToken;
}
