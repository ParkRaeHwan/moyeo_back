package com.example.capstone.plan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import com.example.capstone.plan.dto.response.ScheduleCreateResDto.PlaceResponse;


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

    private final WebClient.Builder webClientBuilder;


    private WebClient getWebClient() {
        return webClientBuilder
                .baseUrl("https://apis.openapi.sk.com")
                .defaultHeader("appKey", appKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public int getTime(String mode, double startLat, double startLng, double endLat, double endLng) {
        try {
            String url = switch (mode) {
                case "walk" -> "/tmap/routes/pedestrian?version=1&format=json";
                case "drive" -> "/tmap/routes?version=1&format=json";
                case "transit" -> "/transit/routes";
                default -> throw new IllegalArgumentException("Invalid mode");
            };

            Map<String, Object> body = new HashMap<>();
            body.put("startX", String.valueOf(startLng));
            body.put("startY", String.valueOf(startLat));
            body.put("endX", String.valueOf(endLng));
            body.put("endY", String.valueOf(endLat));
            body.put("startName", "출발지");
            body.put("endName", "도착지");

            if (mode.equals("transit")) {
                body.put("searchDttm", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm")));
                body.put("count", 1);
                body.put("lang", 0);
                body.put("format", "json");
            } else {
                body.put("reqCoordType", "WGS84GEO");
                body.put("resCoordType", "WGS84GEO");
                body.put("searchOption", 0); // 추천 경로
            }

            String response = getWebClient()
                    .post()
                    .uri(url)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response);

            return switch (mode) {
                case "walk", "drive" ->
                        (int) Math.ceil(root.at("/features/0/properties/totalTime").asDouble(-1) / 60.0);
                case "transit" ->
                        (int) Math.ceil(root.at("/metaData/plan/itineraries/0/totalTime").asDouble(-1) / 60.0);
                default -> -1;
            };

        } catch (Exception e) {
            System.err.println("[Tmap 실패] mode=" + mode + ", from=" + startLat + "," + startLng +
                    " to=" + endLat + "," + endLng);
            e.printStackTrace();
            return -1; // 오류 발생 시 -1 반환
        }
    }
    public void populateTimes(Map<String, List<PlaceResponse>> schedule) {
        PlaceResponse prev = null;

        for (List<PlaceResponse> dayPlaces : schedule.values()) {
            for (PlaceResponse current : dayPlaces) {
                if (prev != null) {
                    int walk = getTime("walk", prev.getLat(), prev.getLng(), current.getLat(), current.getLng());
                    int drive = getTime("drive", prev.getLat(), prev.getLng(), current.getLat(), current.getLng());
                    int transit = getTime("transit", prev.getLat(), prev.getLng(), current.getLat(), current.getLng());

                    current.setWalkTime(walk);
                    current.setDriveTime(drive);
                    current.setTransitTime(transit);
                }
                prev = current;
            }
        }
    }
}
