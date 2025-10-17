package com.example.capstone.plan.dto.response;

import com.example.capstone.plan.dto.common.PlaceDetailDto;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleCreateResDto {
    private String title;                       // 예: 제주 2박 3일 여행
    private LocalDate startDate;
    private LocalDate endDate;
    private List<DailyScheduleBlock> days;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyScheduleBlock {
        private String day;                    // 예: 1일차
        private String date;                   // 예: 2025-08-01
        private int totalEstimatedCost;        // 하루 예산 합계
        private List<PlaceResponse> places;    // 하루치 장소 리스트 (7개)
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlaceResponse {
        private String type;        // 예: 아침, 관광지, 숙소 등
        private String name;        // 실제 상호명
        private String hashtag;     // GPT가 생성한 키워드 (지역+업종)
        private int estimatedCost;  // 예상 비용 (원)
        private double lat;          // 위도
        private double lng;          // 경도
        private Integer walkTime;    // 단위: 초
        private Integer driveTime;
        private Integer transitTime;


        public static PlaceResponse from(PlaceDetailDto src) {
            if (src == null) return null;
            return PlaceResponse.builder()
                    .type(src.getType())
                    .name(src.getName())
                    .hashtag(src.getHashtag())
                    .estimatedCost(src.getEstimatedCost() != null ? src.getEstimatedCost() : 0)
                    .lat(src.getLat() != null ? src.getLat() : 0.0)
                    .lng(src.getLng() != null ? src.getLng() : 0.0)
                    .walkTime(src.getWalkTime())
                    .driveTime(src.getDriveTime())
                    .transitTime(src.getTransitTime())
                    .build();
        }
    }
}