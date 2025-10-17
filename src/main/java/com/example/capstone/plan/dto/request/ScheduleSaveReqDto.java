package com.example.capstone.plan.dto.request;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ScheduleSaveReqDto {

    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<DayRequest> days;

    @Data
    @Builder
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    public static class DayRequest {
        private List<PlaceRequest> places;
    }

    @Data
    @Builder
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    public static class PlaceRequest {
        private String type;
        private String name;
        private String hashtag;
        private int estimatedCost;
        private double lat;
        private double lng;
        private Integer walkTime;
        private Integer driveTime;
        private Integer transitTime;
        private Integer placeOrder;
    }
}
