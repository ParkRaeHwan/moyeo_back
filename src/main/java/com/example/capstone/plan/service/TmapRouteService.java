package com.example.capstone.plan.service;

import com.example.capstone.plan.dto.response.ScheduleCreateResDto.PlaceResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TmapRouteService {

    @Value("${TMAP_API_KEY}")
    private String appKey;

    private final RestTemplateBuilder restTemplateBuilder;
    private final ObjectMapper objectMapper;

    private RestTemplate getRestTemplate() {
        return restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    public int getTime(String mode, double startLat, double startLng, double endLat, double endLng) {
        try {
            String url = switch (mode) {
                case "walk" -> "https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1&format=json";
                case "drive" -> "https://apis.openapi.sk.com/tmap/routes?version=1&format=json";
                case "transit" -> "https://apis.openapi.sk.com/transit/routes";
                default -> throw new IllegalArgumentException("잘못된 이동 모드: " + mode);
            };

            Map<String, Object> body = new HashMap<>();
            body.put("startX", startLng);
            body.put("startY", startLat);
            body.put("endX", endLng);
            body.put("endY", endLat);
            body.put("startName", "출발지");
            body.put("endName", "도착지");

            if ("transit".equals(mode)) {
                body.put("searchDttm", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm")));
                body.put("count", 1);
                body.put("lang", 0);
                body.put("format", "json");
            } else {
                body.put("reqCoordType", "WGS84GEO");
                body.put("resCoordType", "WGS84GEO");
                body.put("searchOption", 0); // 추천 경로
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            headers.set("appKey", appKey);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = getRestTemplate()
                    .exchange(url, HttpMethod.POST, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());

            // 이동 시간 계산
            return switch (mode) {
                case "walk", "drive" ->
                        (int) Math.ceil(root.at("/features/0/properties/totalTime").asDouble(-1) / 60.0);
                case "transit" ->
                        (int) Math.ceil(root.at("/metaData/plan/itineraries/0/totalTime").asDouble(-1) / 60.0);
                default -> -1;
            };

        } catch (HttpStatusCodeException e) {
            String err = String.format("Tmap API 실패 [%s] %s", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException(err, e);
        } catch (Exception e) {
            throw new RuntimeException("Tmap 이동시간 계산 실패: " + e.getMessage(), e);
        }
    }

    public void populateTimes(Map<String, List<PlaceResponse>> schedule) {
        PlaceResponse prev = null;

        for (List<PlaceResponse> dayPlaces : schedule.values()) {
            for (PlaceResponse current : dayPlaces) {
                if (prev != null) {
                    try {
                        int walk = getTime("walk", prev.getLat(), prev.getLng(), current.getLat(), current.getLng());
                        int drive = getTime("drive", prev.getLat(), prev.getLng(), current.getLat(), current.getLng());
                        int transit = getTime("transit", prev.getLat(), prev.getLng(), current.getLat(), current.getLng());

                        current.setWalkTime(walk);
                        current.setDriveTime(drive);
                        current.setTransitTime(transit);
                    } catch (Exception e) {
                        System.err.println("[Tmap 경로 계산 실패] " + e.getMessage());
                        current.setWalkTime(-1);
                        current.setDriveTime(-1);
                        current.setTransitTime(-1);
                    }
                }
                prev = current;
            }
        }
    }
}
