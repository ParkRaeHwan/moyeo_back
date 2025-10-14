package com.example.capstone.chatbot.service;

import com.example.capstone.chatbot.dto.response.*;
import com.example.capstone.chatbot.entity.ChatCategory;
import com.example.capstone.plan.dto.common.KakaoPlaceDto;
import com.example.capstone.plan.entity.City;
import com.example.capstone.plan.service.KakaoMapClient;
import com.example.capstone.plan.service.GeminiClient; // ★ 변경: OpenAiClient → GeminiClient
import com.example.capstone.util.chatbot.FestivalPromptBuilder;
import com.example.capstone.util.chatbot.FoodPromptBuilder;
import com.example.capstone.util.chatbot.HotelPromptBuilder;
import com.example.capstone.util.chatbot.SpotPromptBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@Service
@RequiredArgsConstructor
public class GpsChatService {

    private final OpenWeatherClient openWeatherClient;
    private final KakaoMapClient kakaoMapClient;
    private final FoodPromptBuilder foodPromptBuilder;
    private final HotelPromptBuilder hotelPromptBuilder;
    private final FestivalPromptBuilder festivalPromptBuilder;
    private final SpotPromptBuilder spotPromptBuilder;
    private final GeminiClient geminiClient;            // ★ 변경: 필드 교체
    private final TourApiClient tourApiClient;
    private final ChatBotParseService parseService;
    private final ObjectMapper objectMapper;

    /** 날씨 동일 */
    public WeatherResDto getWeather(double lat, double lng) {
        City city = kakaoMapClient.getCityFromLatLng(lat, lng);  // 시 이름 추출
        String regionName = city.getDisplayName();               // 예: "청주시"

        KakaoPlaceDto place = kakaoMapClient.searchPlace(regionName); // 시 이름 → 위경도
        if (place == null) {
            throw new IllegalArgumentException("해당 도시의 위치 정보를 찾을 수 없습니다.");
        }

        return openWeatherClient.getWeather(
                place.getLatitude(),
                place.getLongitude(),
                regionName
        );
    }

    /** FOOD: Kakao 3곳 → Gemini(JSON 강제) → DTO 파싱 */
    public List<FoodResDto> getFoodList(double lat, double lng) {
        List<KakaoPlaceDto> places = kakaoMapClient.searchPlacesByCategory(lat, lng, "FD6"); // 음식점
        return places.stream()
                .limit(3)
                .map(place -> {
                    try {
                        String prompt = foodPromptBuilder.build(place);
                        String responseJson = geminiClient.callGemini(prompt);
                        return objectMapper.readValue(responseJson, FoodResDto.class);
                    } catch (Exception e) {
                        throw new RuntimeException("GPS 음식점 GPT 처리 실패: " + place.getPlaceName(), e);
                    }
                })
                .collect(Collectors.toList());
    }

    /** HOTEL: Kakao 3곳 → Gemini(JSON 강제) → DTO 파싱 */
    public List<HotelResDto> getHotelList(double lat, double lng) {
        List<KakaoPlaceDto> places = kakaoMapClient.searchPlacesByCategory(lat, lng, "AD5"); // 숙소
        return places.stream()
                .limit(3)
                .map(place -> {
                    try {
                        String prompt = hotelPromptBuilder.build(place);
                        String responseJson = geminiClient.callGemini(prompt);
                        return objectMapper.readValue(responseJson, HotelResDto.class);
                    } catch (Exception e) {
                        throw new RuntimeException("GPS 숙소 GPT 처리 실패: " + place.getPlaceName(), e);
                    }
                })
                .collect(Collectors.toList());
    }

    /** FESTIVAL: TourAPI → Gemini(JSON 요약) → 파싱 (최대 3개) */
    public List<FestivalResDto> getFestivalList(double lat, double lng) {
        JsonNode json = tourApiClient.getFestivalList(lat, lng, LocalDate.now());
        List<JsonNode> rawFestivals = extractFestivalItems(json);

        List<FestivalResDto> result = new ArrayList<>();
        for (JsonNode item : rawFestivals) {
            String prompt = festivalPromptBuilder.buildSingle(item);
            String gptResponseJson = geminiClient.callGemini(prompt);

            try {
                FestivalResDto dto = (FestivalResDto) parseService.parseResponse(ChatCategory.FESTIVAL, gptResponseJson);
                result.add(dto);
            } catch (Exception e) {
                throw new RuntimeException("GPS 축제 조회 중 오류 발생", e);
            }

            if (result.size() >= 3) break;
        }

        return result;
    }

    private List<JsonNode> extractFestivalItems(JsonNode responseJson) {
        JsonNode items = responseJson.at("/response/body/items/item");
        if (items.isMissingNode()) return List.of();
        if (items.isArray()) {
            return StreamSupport.stream(items.spliterator(), false).collect(Collectors.toList());
        } else {
            return List.of(items);
        }
    }


    public List<SpotResDto> getSpotList(double lat, double lng) {
        City city = kakaoMapClient.getCityFromLatLng(lat, lng);

        try {
            String prompt = spotPromptBuilder.build(city);
            String responseJson = geminiClient.callGemini(prompt);

            List<SpotResDto> results = objectMapper.readValue(
                    responseJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, SpotResDto.class)
            );

            Set<String> seen = new HashSet<>();
            results.removeIf(spot -> spot.getName() == null || !seen.add(spot.getName()));

            return results;

        } catch (Exception e) {
            throw new RuntimeException("SPOT 리스트 파싱 실패", e);
        }
    }

}
