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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleRecreateService {

    private final ScheduleRefinerService scheduleRefinerService;
    private final GptRecreatePromptBuilder gptRecreatePromptBuilder;
    private final GptCostPromptBuilder gptCostPromptBuilder;
    private final TmapRouteService tmapRouteService;

    // 생성 서비스와 동일하게 Gemini 사용으로 통일
    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    public ScheduleCreateResDto recreateSchedule(ScheduleRecreateReqDto regenerateRequest) {
        try {
            final ScheduleCreateReqDto request = regenerateRequest.getRequest();
            final List<String> excludePlaceNames = Optional.ofNullable(regenerateRequest.getExcludedNames())
                    .orElseGet(Collections::emptyList);

            // 1) 프롬프트 생성 & Gemini 호출 (JSON 바로)
            final String prompt = gptRecreatePromptBuilder.build(request, excludePlaceNames);
            final JsonNode itineraryNode = Optional.ofNullable(geminiClient.callGeminiAsJsonNode(prompt).get("itinerary"))
                    .orElseThrow(() -> new IllegalStateException("Gemini 응답에 'itinerary' 가 없습니다."));

            // 2) Gemini 응답 -> 날짜별 GptPlaceDto 맵
            final Map<String, List<GptPlaceDto>> gptMap = parseItineraryToMap(itineraryNode);

            // 3) KakaoMap 정제
            final Map<String, List<PlaceResponse>> refinedMap = scheduleRefinerService.refine(gptMap);

            // 4) 이동시간 계산(Tmap)
            tmapRouteService.populateTimes(refinedMap);

            // 5) 예산 계산 (프롬프트 -> Gemini JSON 응답 -> 이름기반 매핑)
            final String costPrompt = gptCostPromptBuilder.build(convertToPlaceDetailMap(refinedMap));
            final JsonNode costJson = geminiClient.callGeminiAsJsonNode(costPrompt);
            applyEstimatedCostsByName(refinedMap, costJson);

            // 6) 응답 조립 (생성과 동일 포맷)
            final List<DailyScheduleBlock> dailyBlocks = new ArrayList<>();
            int dayCounter = 1;


            final Map<String, List<PlaceResponse>> ordered = refinedMap;

            for (Map.Entry<String, List<PlaceResponse>> entry : ordered.entrySet()) {
                final String date = entry.getKey();
                final String day = (dayCounter++) + "일차";
                final List<PlaceResponse> places = entry.getValue();

                final int total = places.stream()
                        .mapToInt(p -> Optional.ofNullable(p.getEstimatedCost()).orElse(0))
                        .sum();

                dailyBlocks.add(DailyScheduleBlock.builder()
                        .day(day)
                        .date(date)
                        .totalEstimatedCost(total)
                        .places(places)
                        .build());
            }

            final long nights = ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate());
            final String title = request.getDestination().getDisplayName() + " " + nights + "박 " + (nights + 1) + "일 여행";

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

    /** 생성 서비스와 동일한 입력 구조: itinerary(JSON 배열) -> 날짜별 GptPlaceDto 맵 */
    private Map<String, List<GptPlaceDto>> parseItineraryToMap(JsonNode itinerary) {
        final Map<String, List<GptPlaceDto>> map = new LinkedHashMap<>();

        for (JsonNode dayNode : itinerary) {
            final String date = Optional.ofNullable(dayNode.get("date")).map(JsonNode::asText)
                    .orElseThrow(() -> new IllegalArgumentException("일자(date) 누락"));

            final JsonNode schedule = Optional.ofNullable(dayNode.get("travelSchedule"))
                    .orElseThrow(() -> new IllegalArgumentException("travelSchedule 누락"));

            final List<GptPlaceDto> places = new ArrayList<>();
            for (JsonNode placeNode : schedule) {
                final String type = Optional.ofNullable(placeNode.get("type")).map(JsonNode::asText).orElse("기타");
                final String nameOrHashtag = Optional.ofNullable(placeNode.get("name")).map(JsonNode::asText)
                        .orElseThrow(() -> new IllegalArgumentException("장소 name 누락"));

                places.add(GptPlaceDto.builder()
                        .name(nameOrHashtag)   // 생성 서비스와 동일하게 '키워드/해시태그성 name'을 그대로 사용
                        .type(type)
                        .location(null)        // 생성 단계에서는 위경도 없음
                        .build());
            }
            map.put(date, places);
        }
        return map;
    }

    /** 비용 프롬프트 입력용 변환 (생성과 동일) */
    private Map<String, List<PlaceDetailDto>> convertToPlaceDetailMap(Map<String, List<PlaceResponse>> input) {
        final Map<String, List<PlaceDetailDto>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<PlaceResponse>> entry : input.entrySet()) {
            final List<PlaceDetailDto> converted = entry.getValue().stream()
                    .map(p -> PlaceDetailDto.builder()
                            .name(p.getName())
                            .type(p.getType())
                            .lat(p.getLat()) // null 가능 -> 프롬프트에서 안전하게 처리
                            .lng(p.getLng())
                            .build())
                    .collect(Collectors.toList());
            result.put(entry.getKey(), converted);
        }
        return result;
    }

    /** 비용 매핑: 이름 정규화 후 매칭 (생성 서비스의 로직과 동일 철학) */
    private void applyEstimatedCostsByName(Map<String, List<PlaceResponse>> refinedMap, JsonNode costJson) {
        // costJson 구조가 (1) 날짜 키 오브젝트 이거나 (2) itinerary 배열일 수 있음. 둘 다 지원.
        if (costJson.has("itinerary") && costJson.get("itinerary").isArray()) {
            // 케이스 (2): itinerary 배열
            for (JsonNode dayNode : costJson.get("itinerary")) {
                final String date = optText(dayNode, "date");
                if (date == null || !refinedMap.containsKey(date)) continue;

                final JsonNode ts = dayNode.get("travelSchedule");
                if (ts == null || !ts.isArray()) continue;

                final List<PlaceResponse> places = refinedMap.get(date);
                mapCostsByName(places, ts);
            }
        } else if (costJson.isObject()) {
            // 케이스 (1): 날짜 키 오브젝트 (totalEstimatedCost 등 특수키 제외)
            final Iterator<String> fields = costJson.fieldNames();
            while (fields.hasNext()) {
                final String date = fields.next();
                if ("totalEstimatedCost".equals(date)) continue;
                if (!refinedMap.containsKey(date)) continue;

                final JsonNode dateBlock = costJson.get(date);
                if (dateBlock == null || !dateBlock.has("travelSchedule")) continue;

                final JsonNode ts = dateBlock.get("travelSchedule");
                if (ts == null || !ts.isArray()) continue;

                final List<PlaceResponse> places = refinedMap.get(date);
                mapCostsByName(places, ts);
            }
        }
    }

    private void mapCostsByName(List<PlaceResponse> places, JsonNode travelSchedule) {
        for (JsonNode placeNode : travelSchedule) {
            final String gptName = optText(placeNode, "name");
            final Integer cost = placeNode.hasNonNull("estimatedCost") ? placeNode.get("estimatedCost").asInt(0) : 0;
            if (gptName == null) continue;

            final String key = normalize(gptName);

            // 1순위: 완전 일치
            Optional<PlaceResponse> exact = places.stream()
                    .filter(p -> normalize(p.getName()).equals(key))
                    .findFirst();

            if (exact.isPresent()) {
                exact.get().setEstimatedCost(cost);
                continue;
            }

            // 2순위: 약한 포함 매칭(지점/띄어쓰기 차이 보정)
            Optional<PlaceResponse> weak = places.stream()
                    .filter(p -> weakEquals(p.getName(), gptName))
                    .findFirst();

            weak.ifPresent(p -> p.setEstimatedCost(cost));
        }
    }

    // === 문자열 정규화/약매칭 유틸 (생성 서비스와 동일 철학) ===
    private static String normalize(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\s\\p{Z}]+", "")
                .replaceAll("[()\\[\\]{}·ㆍ・·••—–-]", "")
                .toLowerCase();
    }

    private static boolean weakEquals(String a, String b) {
        String na = normalize(a);
        String nb = normalize(b);
        return na.equals(nb) || na.contains(nb) || nb.contains(na);
    }

    private static String optText(JsonNode node, String field) {
        return node != null && node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }
}
