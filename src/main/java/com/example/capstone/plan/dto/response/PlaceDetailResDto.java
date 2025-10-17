package com.example.capstone.plan.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceDetailResDto {
    private String name;           // 장소명
    private String type;           // 장소 유형
    private String description;    // GPT로 생성된 한줄 설명
    private String address;        // 카카오맵 주소
    private double lat;            // 위도
    private double lng;            // 경도
    private int estimatedCost;     // 예상 비용
}