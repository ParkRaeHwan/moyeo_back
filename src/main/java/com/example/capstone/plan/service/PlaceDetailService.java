package com.example.capstone.plan.service;

import com.example.capstone.plan.dto.request.PlaceDetailReqDto;
import com.example.capstone.plan.dto.response.PlaceDetailResDto;
import com.example.capstone.util.gpt.GptDescriptionPromptBuilder;
import com.example.capstone.plan.service.KakaoMapClient;
import com.example.capstone.plan.service.OpenAiClient;
import com.example.capstone.plan.dto.common.KakaoPlaceDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlaceDetailService {

    private final GptDescriptionPromptBuilder descriptionPromptBuilder;
    private final KakaoMapClient kakaoMapClient;
    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    public PlaceDetailResDto getPlaceDetail(PlaceDetailReqDto request) {
        try {
            // 1. GPT 한줄 설명 요청
            String prompt = descriptionPromptBuilder.build(request.getName(), request.getType());
            String gptResponse = openAiClient.callGpt(prompt);
            JsonNode descNode = objectMapper.readTree(gptResponse);
            String description = descNode.has("description") ? descNode.get("description").asText() : "";

            // 2. KakaoMap 장소 검색
            KakaoPlaceDto place = kakaoMapClient.searchPlace(request.getName());

            return PlaceDetailResDto.builder()
                    .name(request.getName())
                    .type(request.getType())
                    .description(description)
                    .address(place != null ? place.getAddress() : "")
                    .lat(place != null ? place.getLatitude() : 0.0)
                    .lng(place != null ? place.getLongitude() : 0.0)
                    .estimatedCost(request.getEstimatedCost())
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("상세정보 조회 실패: " + e.getMessage(), e);
        }
    }
}
