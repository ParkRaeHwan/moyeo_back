package com.example.capstone.plan.service;

import com.example.capstone.plan.dto.common.KakaoPlaceDto;
import com.example.capstone.plan.dto.response.ScheduleCreateResDto.PlaceResponse;
import com.example.capstone.plan.dto.response.ScheduleEditResDto;
import com.example.capstone.util.gpt.GptEditPromptBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleEditService {

    private final GptEditPromptBuilder promptBuilder;
    private final GeminiClient geminiClient;     // LLM 호출
    private final KakaoMapClient kakaoMapClient; // 좌표/주소
    private final TmapRouteService tmapRouteService; //  추가: TMAP 이동시간
    private final ObjectMapper objectMapper;

    public ScheduleEditResDto editSchedule(List<String> names) {
        try {
            String prompt = promptBuilder.build(names);
            String gptResponse = geminiClient.callGemini(prompt);

            List<PlaceResponse> places = parseGptResponse(gptResponse);
            int total = places.stream().mapToInt(PlaceResponse::getEstimatedCost).sum();

            return ScheduleEditResDto.builder()
                    .totalEstimatedCost(total)
                    .places(places)
                    .build();

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("일정 수정 중 오류 발생: " + e.getMessage(), e);
        }
    }

    private List<PlaceResponse> parseGptResponse(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);

        JsonNode placesNode;
        if (root.isArray()) {
            placesNode = root;
        } else {
            placesNode = root.path("places");
            if (!placesNode.isArray()) {
                throw new IllegalArgumentException("GPT 응답 내 'places' 필드는 JSON 배열이어야 합니다.");
            }
        }

        List<PlaceResponse> result = new ArrayList<>();
        PlaceResponse prev = null; //  이전 장소 추적 (TMAP 계산용)

        for (JsonNode node : placesNode) {
            String name = node.path("name").asText(null);
            String hashtag = node.path("hashtag").asText(null);
            if (name == null || name.isBlank()) continue;

            KakaoPlaceDto kakao = kakaoMapClient.searchPlace(name);
            if (kakao == null) continue;

            String matchedName = kakao.getPlaceName();
            if (matchedName == null || (!matchedName.contains(name) && !name.contains(matchedName))) continue;

            double currLat = kakao.getLatitude();
            double currLng = kakao.getLongitude();

            Integer walkTime = null;
            Integer driveTime = null;
            Integer transitTime = null;

            // 첫 장소가 아니고, 좌표가 유효하면 이동시간 계산
            if (prev != null && valid(prev.getLat(), prev.getLng()) && valid(currLat, currLng)) {
                int walk    = tmapRouteService.getTime("walk",    prev.getLat(), prev.getLng(), currLat, currLng);
                int drive   = tmapRouteService.getTime("drive",   prev.getLat(), prev.getLng(), currLat, currLng);
                int transit = tmapRouteService.getTime("transit", prev.getLat(), prev.getLng(), currLat, currLng);
                // 실패 시 -1 반환하는 관례 유지 → 그대로 저장
                walkTime = walk;
                driveTime = drive;
                transitTime = transit;
            }

            PlaceResponse place = PlaceResponse.builder()
                    .name(name)
                    .hashtag(hashtag)
                    .type(node.path("type").asText(null))
                    .estimatedCost(node.path("estimatedCost").asInt(0))
                    .walkTime(walkTime)
                    .driveTime(driveTime)
                    .transitTime(transitTime)
                    .lat(currLat)
                    .lng(currLng)
                    .build();

            result.add(place);
            prev = place;
        }
        return result;
    }

    private boolean valid(double lat, double lng) {
        return !(lat == 0.0 || lng == 0.0);
    }
}
