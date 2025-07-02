package com.example.capstone.plan.service;

import com.example.capstone.plan.dto.common.FromPreviousDto;
import com.example.capstone.plan.dto.common.PlaceDetailDto;
import com.example.capstone.plan.dto.request.ScheduleRecreateReqDto;
import com.example.capstone.plan.dto.request.ScheduleSaveReqDto;
import com.example.capstone.plan.dto.request.ScheduleCreateReqDto;
import com.example.capstone.plan.dto.response.ScheduleSaveResDto;
import com.example.capstone.plan.dto.response.FullScheduleResDto;
import com.example.capstone.plan.dto.response.FullScheduleResDto.PlaceResponse;
import com.example.capstone.plan.dto.response.FullScheduleResDto.DailyScheduleBlock;
import com.example.capstone.plan.dto.response.SimpleScheduleResDto;
import com.example.capstone.plan.entity.TravelDay;
import com.example.capstone.plan.entity.FromPrevious;
import com.example.capstone.plan.entity.TravelPlace;
import com.example.capstone.plan.entity.TravelSchedule;
import com.example.capstone.plan.repository.DayRepository;
import com.example.capstone.plan.repository.PlaceRepository;
import com.example.capstone.plan.repository.ScheduleRepository;
import com.example.capstone.user.entity.UserEntity;
import com.example.capstone.user.repository.UserRepository;
import com.example.capstone.util.gpt.GptCostAndTimePromptBuilder;
import com.example.capstone.util.gpt.GptPlaceDescriptionPromptBuilder;
import com.example.capstone.util.gpt.GptRecreatePromptBuilder;
import com.example.capstone.util.gpt.GptScheduleStructurePromptBuilder;
import com.example.capstone.util.oauth2.dto.CustomOAuth2User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleRefinerService scheduleRefinerService;
    private final GptScheduleStructurePromptBuilder structurePromptBuilder;
    private final GptPlaceDescriptionPromptBuilder descriptionPromptBuilder;
    private final GptCostAndTimePromptBuilder costAndTimePromptBuilder;
    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;


    public FullScheduleResDto generateFullSchedule(ScheduleCreateReqDto request) {
        try {
            String schedulePrompt = structurePromptBuilder.build(request);
            String gptResponse = openAiClient.callGpt(schedulePrompt);

            List<PlaceDetailDto> places = scheduleRefinerService.getRefinedPlacesFromPrompt(gptResponse);

            List<String> placeNames = places.stream().map(PlaceDetailDto::getName).toList();
            String descPrompt = descriptionPromptBuilder.build(placeNames);
            String descResponse = openAiClient.callGpt(descPrompt);
            Map<String, String> descriptionMap = parseDescriptionMap(descResponse);
            for (PlaceDetailDto place : places) {
                String desc = descriptionMap.get(place.getName());
                if (desc != null) place.setDescription(desc);
            }

            String costPrompt = costAndTimePromptBuilder.build(places);
            String costResponse = openAiClient.callGpt(costPrompt);
            List<FullScheduleResDto.PlaceResponse> finalPlaces =
                    scheduleRefinerService.parseGptResponse(costResponse, places);
            List<PlaceDetailDto> enrichedPlaces = finalPlaces.stream().map(PlaceResponse::toDto).toList();

            Map<Integer, List<PlaceDetailDto>> groupedByDayIndex = new LinkedHashMap<>();
            int days = (int) ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate()) + 1;
            for (int i = 0; i < days; i++) groupedByDayIndex.put(i, new ArrayList<>());
            for (int i = 0; i < enrichedPlaces.size(); i++) {
                int dayIndex = i / 7;
                if (dayIndex < days) groupedByDayIndex.get(dayIndex).add(enrichedPlaces.get(i));
            }

            List<DailyScheduleBlock> blocks = new ArrayList<>();
            for (int i = 0; i < days; i++) {
                LocalDate date = request.getStartDate().plusDays(i);
                String day = (i + 1) + "일차";
                List<PlaceDetailDto> dayPlaces = groupedByDayIndex.get(i);
                int total = dayPlaces.stream().map(PlaceDetailDto::getEstimatedCost)
                        .filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
                List<PlaceResponse> placeResponses = dayPlaces.stream().map(PlaceResponse::from).toList();
                blocks.add(new DailyScheduleBlock(day, date.toString(), total, placeResponses));
            }

            String destination = request.getDestination().getDisplayName();
            long nights = ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate());
            String title = destination + " " + nights + "박 " + (nights + 1) + "일 여행";

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