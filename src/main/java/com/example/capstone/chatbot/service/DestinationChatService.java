package com.example.capstone.chatbot.service;

import com.example.capstone.chatbot.dto.response.*;
import com.example.capstone.chatbot.entity.ChatCategory;
import com.example.capstone.plan.dto.common.KakaoPlaceDto;
import com.example.capstone.plan.service.KakaoMapClient;
import com.example.capstone.util.chatbot.FestivalPromptBuilder;
import com.example.capstone.util.chatbot.FoodPromptBuilder;
import com.example.capstone.util.chatbot.HotelPromptBuilder;
import com.example.capstone.plan.entity.City;
import com.example.capstone.plan.service.GeminiClient; // ★ 변경: OpenAiClient → GeminiClient
import com.example.capstone.util.chatbot.SpotPromptBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.capstone.chatbot.service.ChatBotParseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class DestinationChatService {

    private final OpenWeatherClient openWeatherClient;
    private final KakaoMapClient kakaoMapClient;
    private final FoodPromptBuilder foodPromptBuilder;
    private final HotelPromptBuilder hotelPromptBuilder;
    private final FestivalPromptBuilder festivalPromptBuilder;
    private final SpotPromptBuilder spotPromptBuilder;
    private final GeminiClient geminiClient;
    private final TourApiClient tourApiClient;
    private final ChatBotParseService parseService;
    private final ObjectMapper objectMapper;

    public WeatherResDto getWeather(City city) {
        String cityName = city.getDisplayName();
        KakaoPlaceDto place = kakaoMapClient.searchPlace(cityName);

        if (place == null) {
            throw new IllegalArgumentException("해당 도시의 위치 정보를 찾을 수 없습니다.");
        }

        return openWeatherClient.getWeather(
                place.getLatitude(),
                place.getLongitude(),
                city.getDisplayName()
        );
    }

    public List<FoodResDto> getFoodList(City city) {
        String keyword = city.getDisplayName();
        String categoryCode = "FD6";

        List<KakaoPlaceDto> places = kakaoMapClient.searchPlacesWithCategory(keyword, categoryCode);

        return places.stream()
                .limit(3)
                .map(place -> {
                    try {
                        String prompt = foodPromptBuilder.build(place);
                        String responseJson = geminiClient.callGemini(prompt); // ★ 변경
                        return objectMapper.readValue(responseJson, FoodResDto.class);
                    } catch (Exception e) {
                        throw new RuntimeException("Food GPT 처리 실패: " + place.getPlaceName(), e);
                    }
                })
                .collect(Collectors.toList());
    }

    public List<HotelResDto> getHotelList(City city) {
        String keyword = city.getDisplayName();
        String categoryCode = "AD5";

        List<KakaoPlaceDto> places = kakaoMapClient.searchPlacesWithCategory(keyword, categoryCode);

        return places.stream()
                .limit(3)
                .map(place -> {
                    try {
                        String prompt = hotelPromptBuilder.build(place);
                        String responseJson = geminiClient.callGemini(prompt); // ★ 변경
                        return objectMapper.readValue(responseJson, HotelResDto.class);
                    } catch (Exception e) {
                        throw new RuntimeException("Hotel GPT 처리 실패: " + place.getPlaceName(), e);
                    }
                })
                .collect(Collectors.toList());
    }

    public List<FestivalResDto> getFestivalList(City city) {
        JsonNode json = tourApiClient.getFestivalListByCity(city, LocalDate.now());
        List<JsonNode> rawFestivals = extractFestivalItems(json);

        List<FestivalResDto> result = new ArrayList<>();
        for (JsonNode item : rawFestivals) {
            String prompt = festivalPromptBuilder.buildSingle(item);
            String gptResponseJson = geminiClient.callGemini(prompt); // ★ 변경

            try {
                FestivalResDto dto = (FestivalResDto) parseService.parseResponse(ChatCategory.FESTIVAL, gptResponseJson);
                result.add(dto);
            } catch (Exception e) {
                // 파싱 실패는 스킵
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

    public List<SpotResDto> getSpot(City city) throws Exception {
        String prompt = spotPromptBuilder.build(city);
        String json = geminiClient.callGemini(prompt);

        List<SpotResDto> results = (List<SpotResDto>) parseService.parseResponse(ChatCategory.SPOT, json);

        return results;
    }
}
