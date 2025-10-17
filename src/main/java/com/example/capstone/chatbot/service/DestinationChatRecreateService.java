package com.example.capstone.chatbot.service;

import com.example.capstone.chatbot.dto.request.ChatBotRecreateReqDto;
import com.example.capstone.chatbot.dto.response.FestivalResDto;
import com.example.capstone.chatbot.dto.response.FoodResDto;
import com.example.capstone.chatbot.dto.response.HotelResDto;
import com.example.capstone.chatbot.dto.response.SpotResDto;
import com.example.capstone.chatbot.entity.ChatCategory;
import com.example.capstone.plan.dto.common.KakaoPlaceDto;
import com.example.capstone.plan.entity.City;
import com.example.capstone.plan.service.KakaoMapClient;
import com.example.capstone.plan.service.GeminiClient; // ★ 변경
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
public class DestinationChatRecreateService {

    private final GeminiClient geminiClient;     // ★ 변경: OpenAiClient → GeminiClient
    private final ChatBotParseService parseService;
    private final KakaoMapClient kakaoMapClient;
    private final TourApiClient tourApiClient;

    private final FoodRecreatePromptBuilder foodRecreatePromptBuilder;
    private final HotelRecreatePromptBuilder hotelRecreatePromptBuilder;
    private final FestivalRecreatePromptBuilder festivalRecreatePromptBuilder;
    private final SpotRecreatePromptBuilder spotRecreatePromptBuilder;

    public List<FoodResDto> recreateFood(ChatBotRecreateReqDto req) {
        try {
            List<KakaoPlaceDto> topPlaces =
                    kakaoMapClient.searchTopPlacesByCityAndCategory(req.getCity(), "FD6", 10);

            return topPlaces.stream()
                    .filter(place -> !req.getExcludedNames().contains(place.getPlaceName()))
                    .limit(3)
                    .map(place -> {
                        try {
                            String prompt = foodRecreatePromptBuilder.build(place);
                            String response = geminiClient.callGemini(prompt); // ★ 변경
                            // parseService가 배열을 기대하므로 단일 객체를 배열로 감싸서 전달
                            return ((List<FoodResDto>) parseService.parseResponse(
                                    ChatCategory.FOOD, "[" + response + "]"
                            )).get(0);
                        } catch (Exception e) {
                            throw new RuntimeException("음식점 GPT 파싱 실패: " + place.getPlaceName(), e);
                        }
                    })
                    .toList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    public List<HotelResDto> recreateHotel(ChatBotRecreateReqDto req) {
        try {
            List<KakaoPlaceDto> topPlaces =
                    kakaoMapClient.searchTopPlacesByCityAndCategory(req.getCity(), "AD5", 10);

            return topPlaces.stream()
                    .filter(place -> !req.getExcludedNames().contains(place.getPlaceName()))
                    .limit(3)
                    .map(place -> {
                        try {
                            String prompt = hotelRecreatePromptBuilder.build(place);
                            String response = geminiClient.callGemini(prompt); // ★ 변경
                            return ((List<HotelResDto>) parseService.parseResponse(
                                    ChatCategory.HOTEL, "[" + response + "]"
                            )).get(0);
                        } catch (Exception e) {
                            throw new RuntimeException("숙소 GPT 파싱 실패: " + place.getPlaceName(), e);
                        }
                    })
                    .toList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    public List<FestivalResDto> recreateFestival(ChatBotRecreateReqDto req) {
        try {
            JsonNode filteredJson = tourApiClient.getFestivalListByCityExcluding(
                    req.getCity(), LocalDate.now(), req.getExcludedNames());

            List<JsonNode> rawFestivals = extractFestivalItems(filteredJson);
            List<FestivalResDto> result = new ArrayList<>();

            for (JsonNode item : rawFestivals) {
                try {
                    String prompt = festivalRecreatePromptBuilder.build(item);
                    String gptResponse = geminiClient.callGemini(prompt); // ★ 변경
                    FestivalResDto dto = (FestivalResDto) parseService.parseResponse(
                            ChatCategory.FESTIVAL, gptResponse);
                    result.add(dto);
                } catch (Exception e) {
                    System.err.println("[Festival GPT 파싱 실패] 일부 항목 건너뜀: " + e.getMessage());
                }
                if (result.size() >= 3) break;
            }

            return result;

        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    public List<SpotResDto> recreateSpot(ChatBotRecreateReqDto req) {
        try {
            City city = req.getCity();
            List<String> excludedNames = new ArrayList<>(req.getExcludedNames());
            List<SpotResDto> result = new ArrayList<>();

            for (int i = 0; i < 3; i++) {
                try {
                    String prompt = spotRecreatePromptBuilder.build(i, city, null, null, excludedNames);
                    String response = geminiClient.callGemini(prompt); // ★ 변경
                    SpotResDto dto = (SpotResDto) parseService.parseResponse(ChatCategory.SPOT, response);

                    if (dto == null || dto.getName() == null || excludedNames.contains(dto.getName())) {
                        i--; // 실패/중복 시 재시도
                        continue;
                    }

                    result.add(dto);
                    excludedNames.add(dto.getName());
                } catch (Exception e) {
                    System.err.println("[Spot GPT 파싱 실패] i=" + i + ": " + e.getMessage());
                    i--; // 실패했으니 반복 재시도
                }
            }

            return result;

        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    private List<JsonNode> extractFestivalItems(JsonNode responseJson) {
        if (responseJson.isArray()) {
            return StreamSupport.stream(responseJson.spliterator(), false).collect(Collectors.toList());
        }
        return List.of();
    }
}
