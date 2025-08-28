package com.example.capstone.plan.service;

import com.example.capstone.plan.dto.common.GptPlaceDto;
import com.example.capstone.plan.dto.common.PlaceDetailDto;
import com.example.capstone.plan.dto.request.ScheduleCreateReqDto;
import com.example.capstone.plan.dto.request.ScheduleRecreateReqDto;
import com.example.capstone.plan.dto.response.ScheduleCreateResDto;
import com.example.capstone.plan.dto.response.ScheduleCreateResDto.PlaceResponse;
import com.example.capstone.plan.dto.response.ScheduleCreateResDto.DailyScheduleBlock;
import com.example.capstone.util.gpt.GptRecreatePromptBuilder;
import com.example.capstone.util.gpt.GptCostPromptBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ScheduleRecreateService {

    private final ScheduleRefinerService scheduleRefinerService;
    private final GptRecreatePromptBuilder gptRecreatePromptBuilder;
    private final GptCostPromptBuilder gptCostPromptBuilder;
    private final TmapRouteService tmapRouteService;
    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    public ScheduleCreateResDto recreateSchedule(ScheduleRecreateReqDto regenerateRequest) {
        try {
            ScheduleCreateReqDto request = regenerateRequest.getRequest();
            List<String> excludePlaceNames = regenerateRequest.getExcludedNames();

            // 1. GPT 프롬프트 생성 및 호출
            String prompt = gptRecreatePromptBuilder.build(request, excludePlaceNames);
            String gptResponse = openAiClient.callGpt(prompt);

            // 2. GPT 응답 → GptPlaceDto 맵으로 파싱
            Map<String, List<GptPlaceDto>> gptMap = parseGptResponseToMap(gptResponse);

            // 3. KakaoMap 정제
            Map<String, List<PlaceResponse>> refinedMap = scheduleRefinerService.refine(gptMap);

            // 4. 이동시간 계산
            tmapRouteService.populateTimes(refinedMap);

            // 5. 예산 계산
            String costPrompt = gptCostPromptBuilder.build(convertToPlaceDetailMap(refinedMap));
            String costResponse = openAiClient.callGpt(costPrompt);
            applyEstimatedCosts(refinedMap, costResponse);

            // 6. ScheduleCreateResDto 조립
            List<DailyScheduleBlock> dailyBlocks = new ArrayList<>();
            int dayCounter = 1;

            for (Map.Entry<String, List<PlaceResponse>> entry : refinedMap.entrySet()) {
                String date = entry.getKey();
                String day = (dayCounter++) + "일차";

                List<PlaceResponse> places = entry.getValue();
                int total = places.stream().mapToInt(p -> Optional.ofNullable(p.getEstimatedCost()).orElse(0)).sum();

                dailyBlocks.add(DailyScheduleBlock.builder()
                        .day(day)
                        .date(date)
                        .totalEstimatedCost(total)
                        .places(places)
                        .build());
            }

            long nights = ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate());
            String title = request.getDestination().getDisplayName() + " " + nights + "박 " + (nights + 1) + "일 여행";

            return ScheduleCreateResDto.builder()
                    .title(title)
                    .startDate(request.getStartDate())
                    .endDate(request.getEndDate())
                    .days(dailyBlocks)
                    .build();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Map<String, List<GptPlaceDto>> parseGptResponseToMap(String gptJson) throws Exception {
        Map<String, List<GptPlaceDto>> map = new LinkedHashMap<>();
        JsonNode itinerary = Optional.ofNullable(objectMapper.readTree(gptJson).get("itinerary"))
                .orElseThrow(() -> new IllegalArgumentException("GPT 응답에 itinerary 누락"));

        for (JsonNode dayNode : itinerary) {
            String date = Optional.ofNullable(dayNode.get("date")).map(JsonNode::asText).orElse("0000-00-00");
            List<GptPlaceDto> places = new ArrayList<>();

            JsonNode schedule = Optional.ofNullable(dayNode.get("travelSchedule")).orElseThrow();
            for (JsonNode placeNode : schedule) {
                String type = Optional.ofNullable(placeNode.get("type")).map(JsonNode::asText).orElse("기타");
                String name = Optional.ofNullable(placeNode.get("name")).map(JsonNode::asText).orElse("이름 없음");

                places.add(GptPlaceDto.builder()
                        .name(name)
                        .type(type)
                        .location(null)
                        .build());
            }

            map.put(date, places);
        }

        return map;
    }

    private Map<String, List<PlaceDetailDto>> convertToPlaceDetailMap(Map<String, List<PlaceResponse>> input) {
        Map<String, List<PlaceDetailDto>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<PlaceResponse>> entry : input.entrySet()) {
            List<PlaceDetailDto> converted = entry.getValue().stream()
                    .map(p -> PlaceDetailDto.builder()
                            .name(p.getName())
                            .type(p.getType())
                            .lat(Optional.ofNullable(p.getLat()).orElse(0.0))
                            .lng(Optional.ofNullable(p.getLng()).orElse(0.0))
                            .build())
                    .toList();
            result.put(entry.getKey(), converted);
        }
        return result;
    }

    private void applyEstimatedCosts(Map<String, List<PlaceResponse>> schedule, String gptResponse) throws Exception {
        JsonNode root = objectMapper.readTree(gptResponse);
        for (JsonNode dayNode : root) {
            String date = Optional.ofNullable(dayNode.get("date"))
                    .map(JsonNode::asText).orElse(null);
            if (date == null || !schedule.containsKey(date)) continue;

            List<PlaceResponse> places = schedule.get(date);
            JsonNode travelSchedule = Optional.ofNullable(dayNode.get("travelSchedule")).orElse(null);
            if (travelSchedule == null || !travelSchedule.isArray()) continue;

            for (int i = 0; i < travelSchedule.size(); i++) {
                int cost = Optional.ofNullable(travelSchedule.get(i).get("estimatedCost"))
                        .map(JsonNode::asInt).orElse(0);
                if (i < places.size()) {
                    places.get(i).setEstimatedCost(cost);
                }
            }
        }
    }
}
