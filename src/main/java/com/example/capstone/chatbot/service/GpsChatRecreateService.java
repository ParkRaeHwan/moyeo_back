package com.example.capstone.chatbot.service;

import com.example.capstone.chatbot.dto.response.FestivalResDto;
import com.example.capstone.chatbot.dto.response.SpotResDto;
import com.example.capstone.chatbot.dto.response.FoodResDto;
import com.example.capstone.chatbot.dto.response.HotelResDto;
import com.example.capstone.chatbot.entity.ChatCategory;
import com.example.capstone.plan.dto.common.KakaoPlaceDto;
import com.example.capstone.plan.service.KakaoMapClient;
import com.example.capstone.plan.service.GeminiClient; // ★ 변경: OpenAiClient → GeminiClient
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
    private final GeminiClient geminiClient;
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
                            String response = geminiClient.callGemini(prompt); // ★ 변경
                            // 기존 파서가 배열을 기대한다면, 단일 객체를 배열로 감싸서 전달
                            return ((List<FoodResDto>) parseService.parseResponse(
                                    ChatCategory.FOOD, "[" + response + "]"
                            )).get(0);
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
                            String response = geminiClient.callGemini(prompt); // ★ 변경
                            return ((List<HotelResDto>) parseService.parseResponse(
                                    ChatCategory.HOTEL, "[" + response + "]"
                            )).get(0);
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
                    String gptResponse = geminiClient.callGemini(prompt); // ★ 변경
                    FestivalResDto dto = (FestivalResDto) parseService.parseResponse(ChatCategory.FESTIVAL, gptResponse);
                    result.add(dto);
                } catch (Exception e) {
                    // 파싱 실패는 스킵
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
            // 1️ 제외할 장소 리스트 복사
            List<String> updatedExcluded = new ArrayList<>(excludedNames);

            // 2️ 프롬프트 한 번만 생성 (한 번에 3개 관광지 요청)
            String prompt = spotRecreatePromptBuilder.build(0, null, lat, lng, updatedExcluded);

            // 3️ Gemini 모델 호출
            String responseJson = geminiClient.callGemini(prompt);

            // 4 GPT 응답을 Spot 리스트로 파싱 (ChatBotParseService 사용)
            List<SpotResDto> results = (List<SpotResDto>) parseService.parseResponse(ChatCategory.SPOT, responseJson);

            // 5 null 또는 중복된 이름 제거 + 제외 목록 반영
            if (results == null || results.isEmpty()) {
                return List.of();
            }

            Set<String> seen = new HashSet<>(updatedExcluded);
            results.removeIf(spot ->
                    spot.getName() == null ||      // 이름 누락 제거
                            !seen.add(spot.getName())      // 중복 제거
            );

            // 6️ 최종 결과 반환
            return results.isEmpty() ? List.of() : results;

        } catch (Exception e) {
            throw new RuntimeException("GPS 관광지 재조회 실패", e);
        }
    }


}
