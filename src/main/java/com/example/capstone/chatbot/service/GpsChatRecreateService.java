package com.example.capstone.chatbot.service;

import com.example.capstone.chatbot.dto.response.FestivalResDto;
import com.example.capstone.chatbot.dto.response.SpotResDto;
import com.example.capstone.chatbot.dto.response.FoodResDto;
import com.example.capstone.chatbot.dto.response.HotelResDto;
import com.example.capstone.chatbot.entity.ChatCategory;
import com.example.capstone.plan.dto.common.KakaoPlaceDto;
import com.example.capstone.plan.service.KakaoMapClient;
import com.example.capstone.plan.service.OpenAiClient;
import com.example.capstone.util.chatbot.recreate.*;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class GpsChatRecreateService {

    private final KakaoMapClient kakaoMapClient;
    private final OpenAiClient openAiClient;
    private final ChatBotParseService parseService;
    private final TourApiClient tourApiClient;

    private final FoodRecreatePromptBuilder foodRecreatePromptBuilder;
    private final HotelRecreatePromptBuilder hotelRecreatePromptBuilder;
    private final FestivalRecreatePromptBuilder festivalRecreatePromptBuilder;
    private final SpotRecreatePromptBuilder spotRecreatePromptBuilder;

    public List<FoodResDto> recreateFood(double lat, double lng, List<String> excludedNames) {
        try {
            List<KakaoPlaceDto> topPlaces = kakaoMapClient.searchTopPlacesByCategory(lat, lng, "FD6", 10); // 음식점

            return topPlaces.stream()
                    .filter(place -> !excludedNames.contains(place.getPlaceName()))
                    .limit(3)
                    .map(place -> {
                        try {
                            String prompt = foodRecreatePromptBuilder.build(place);
                            String response = openAiClient.callGpt(prompt);
                            return ((List<FoodResDto>) parseService.parseResponse(ChatCategory.FOOD, "[" + response + "]")).get(0);
                        } catch (Exception e) {
                            throw new RuntimeException("GPS 음식점 GPT 파싱 실패: " + place.getPlaceName(), e);
                        }
                    })
                    .toList();

        } catch (Exception e) {
            throw new RuntimeException("GPS 음식점 재조회 실패", e);
        }
    }

    public List<HotelResDto> recreateHotel(double lat, double lng, List<String> excludedNames) {
        try {
            List<KakaoPlaceDto> topPlaces = kakaoMapClient.searchTopPlacesByCategory(lat, lng, "AD5", 10); // 숙소

            return topPlaces.stream()
                    .filter(place -> !excludedNames.contains(place.getPlaceName()))
                    .limit(3)
                    .map(place -> {
                        try {
                            String prompt = hotelRecreatePromptBuilder.build(place);
                            String response = openAiClient.callGpt(prompt);
                            return ((List<HotelResDto>) parseService.parseResponse(ChatCategory.HOTEL, "[" + response + "]")).get(0);
                        } catch (Exception e) {
                            throw new RuntimeException("GPS 숙소 GPT 파싱 실패: " + place.getPlaceName(), e);
                        }
                    })
                    .toList();

        } catch (Exception e) {
            throw new RuntimeException("GPS 숙소 재조회 실패", e);
        }
    }

    public List<FestivalResDto> recreateFestival(double lat, double lng, List<String> excludedNames) {
        try {
            JsonNode filteredJson = tourApiClient.getFestivalListByGpsExcluding(lat, lng, LocalDate.now(), excludedNames);
            List<JsonNode> rawFestivals = extractFestivalItems(filteredJson);

            List<FestivalResDto> result = new ArrayList<>();
            for (JsonNode item : rawFestivals) {
                try {
                    String prompt = festivalRecreatePromptBuilder.build(item);
                    String gptResponse = openAiClient.callGpt(prompt);
                    FestivalResDto dto = (FestivalResDto) parseService.parseResponse(ChatCategory.FESTIVAL, gptResponse);
                    result.add(dto);
                } catch (Exception e) {
                }

                if (result.size() >= 3) break;
            }

            return result;

        } catch (Exception e) {
            throw new RuntimeException("GPS 기반 축제 재조회 중 오류 발생", e);
        }
    }

    private List<JsonNode> extractFestivalItems(JsonNode responseJson) {
        if (responseJson.isArray()) {
            return StreamSupport.stream(responseJson.spliterator(), false).collect(Collectors.toList());
        }
        return List.of();
    }

    public List<SpotResDto> recreateSpot(double lat, double lng, List<String> excludedNames) {
        try {
            List<String> updatedExcluded = new ArrayList<>(excludedNames);
            List<SpotResDto> results = new ArrayList<>();

            int i = 0;
            while (results.size() < 3) {
                String prompt = spotRecreatePromptBuilder.build(i, null, lat, lng, updatedExcluded);
                String response = openAiClient.callGpt(prompt);

                SpotResDto dto;
                try {
                    dto = (SpotResDto) parseService.parseResponse(ChatCategory.SPOT, response);
                } catch (Exception e) {
                    continue; // 파싱 실패 → 다음 반복으로 넘어감
                }

                if (dto == null || updatedExcluded.contains(dto.getName())) {
                    continue;
                }

                results.add(dto);
                updatedExcluded.add(dto.getName());
                i++;
            }

            return results;

        } catch (Exception e) {
            throw new RuntimeException("GPS 관광지 재조회 실패", e);
        }
    }
}
