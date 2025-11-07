package com.example.capstone.plan.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleResaveReqDto {

    private List<DayRequest> days;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DayRequest {
        private String day;                 // 예: "1일차"
        private String date;                // 예: "2025-11-10"
        private Integer totalEstimatedCost; // 예: 153000
        private List<PlaceRequest> places;  // 하루치 장소 목록
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlaceRequest {
        private String type;
        private String name;
        private String hashtag;
        private Integer estimatedCost;
        private Double lat;
        private Double lng;
        private Integer walkTime;
        private Integer driveTime;
        private Integer transitTime;
    }
}
