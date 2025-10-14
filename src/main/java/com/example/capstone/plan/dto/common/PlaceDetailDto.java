package com.example.capstone.plan.dto.common;

import lombok.*;

@Data
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PlaceDetailDto {
    private String name;
    private String type;
    private Double lat;
    private Double lng;
    private Integer estimatedCost;
    private Integer walkTime;
    private Integer driveTime;
    private Integer transitTime;
    private String hashtag;


}
