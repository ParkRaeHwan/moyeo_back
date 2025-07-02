package com.example.capstone.plan.service;

import com.example.capstone.plan.dto.common.PlaceDetailDto;
import com.example.capstone.plan.dto.request.ScheduleCreateReqDto;
import com.example.capstone.plan.dto.request.ScheduleRecreateReqDto;
import com.example.capstone.plan.dto.response.FullScheduleResDto;
import com.example.capstone.util.gpt.GptCostAndTimePromptBuilder;
import com.example.capstone.util.gpt.GptPlaceDescriptionPromptBuilder;
import com.example.capstone.util.gpt.GptRecreatePromptBuilder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ScheduleRecreateService {

    private final ScheduleRefinerService scheduleRefinerService;
    private final GptPlaceDescriptionPromptBuilder descriptionPromptBuilder;
    private final GptCostAndTimePromptBuilder costAndTimePromptBuilder;
    private final GptRecreatePromptBuilder gptRecreatePromptBuilder;
    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    public FullScheduleResDto recreateSchedule(ScheduleRecreateReqDto regenerateRequest) {
        try {
            ScheduleCreateReqDto request = regenerateRequest.getRequest();
            List<String> excludePlaceNames = regenerateRequest.getExcludedNames();

            // GPT 프롬프트 생성 및 호출
            String prompt = gptRecreatePromptBuilder.build(request, excludePlaceNames);
            String gptResponse = openAiClient.callGpt(prompt);

            // 장소 정제
            List<PlaceDetailDto> refinedPlaces = scheduleRefinerService.getRefinedPlacesFromPrompt(gptResponse);

            // 한줄 설명 생성
            List<String> placeNames = refinedPlaces.stream().map(PlaceDetailDto::getName).toList();
            String descPrompt = descriptionPromptBuilder.build(placeNames);
            String descResponse = openAiClient.callGpt(descPrompt);
            Map<String, String> descriptionMap = parseDescriptionMap(descResponse);
            for (PlaceDetailDto place : refinedPlaces) {
                place.setDescription(descriptionMap.getOrDefault(place.getName(), ""));
            }

            // 예산 및 이동시간 생성
            String costPrompt = costAndTimePromptBuilder.build(refinedPlaces);
            String costResponse = openAiClient.callGpt(costPrompt);
            List<FullScheduleResDto.PlaceResponse> finalPlaces =
                    scheduleRefinerService.parseGptResponse(costResponse, refinedPlaces);

            // 일정 구성
            long nights = ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate());
            long days = nights + 1;

            List<FullScheduleResDto.DailyScheduleBlock> blocks = new ArrayList<>();
            int placesPerDay = finalPlaces.size() / (int) days;
            int index = 0;

            for (int i = 0; i < days; i++) {
                String day = (i + 1) + "일차";
                String date = request.getStartDate().plusDays(i).toString();

                int remaining = finalPlaces.size() - index;
                int currentDayCount = i == days - 1 ? remaining : placesPerDay;

                List<FullScheduleResDto.PlaceResponse> dailyPlaces =
                        finalPlaces.subList(index, index + currentDayCount);

                int total = dailyPlaces.stream()
                        .mapToInt(FullScheduleResDto.PlaceResponse::getEstimatedCost)
                        .sum();

                blocks.add(new FullScheduleResDto.DailyScheduleBlock(day, date, total, dailyPlaces));
                index += currentDayCount;
            }

            String title = request.getDestination().getDisplayName() + " " + nights + "박 " + days + "일 여행";
            return new FullScheduleResDto(title, request.getStartDate(), request.getEndDate(), blocks);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Map<String, String> parseDescriptionMap(String gptResponse) {
        try {
            return objectMapper.readValue(gptResponse, new TypeReference<>() {});
        } catch (Exception e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }
}
