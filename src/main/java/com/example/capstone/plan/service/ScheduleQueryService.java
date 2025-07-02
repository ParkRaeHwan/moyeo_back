package com.example.capstone.plan.service;

import com.example.capstone.plan.dto.common.FromPreviousDto;
import com.example.capstone.plan.dto.common.PlaceDetailDto;
import com.example.capstone.plan.dto.response.FullScheduleResDto;
import com.example.capstone.plan.dto.response.SimpleScheduleResDto;
import com.example.capstone.plan.entity.TravelPlace;
import com.example.capstone.plan.entity.TravelSchedule;
import com.example.capstone.plan.repository.DayRepository;
import com.example.capstone.plan.repository.PlaceRepository;
import com.example.capstone.plan.repository.ScheduleRepository;
import com.example.capstone.user.entity.UserEntity;
import com.example.capstone.user.repository.UserRepository;
import com.example.capstone.util.oauth2.dto.CustomOAuth2User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ScheduleQueryService {

    private final ScheduleRepository scheduleRepository;
    private final UserRepository userRepository;

    public List<SimpleScheduleResDto> getSimpleScheduleList(CustomOAuth2User userDetails) {
        String providerId = userDetails.getProviderId();
        UserEntity user = userRepository.findByProviderId(providerId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        List<TravelSchedule> travelSchedules = scheduleRepository.findByUserId(user.getId());
        return travelSchedules.stream()
                .map(schedule -> new SimpleScheduleResDto(
                        schedule.getId(),
                        schedule.getTitle(),
                        schedule.getStartDate(),
                        schedule.getEndDate(),
                        formatDday(calculateDDay(schedule.getStartDate()))
                )).toList();
    }

    public FullScheduleResDto getFullSchedule(Long scheduleId, CustomOAuth2User userDetails) {
        String providerId = userDetails.getProviderId();
        UserEntity user = userRepository.findByProviderId(providerId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        TravelSchedule schedule = scheduleRepository.findByIdAndUserId(scheduleId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("접근 권한이 없습니다."));

        List<PlaceDetailDto> places = getPlacesFromDatabase(scheduleId);
        return convertToBlockStructure(places, schedule);
    }


    public List<PlaceDetailDto> getPlacesFromDatabase(Long scheduleId) {
        List<TravelPlace> travelPlaces = scheduleRepository.findAllPlacesByScheduleId(scheduleId);
        return travelPlaces.stream()
                .map(place -> PlaceDetailDto.builder()
                        .name(place.getName())
                        .type(place.getType())
                        .lat(place.getLat())
                        .lng(place.getLng())
                        .address(place.getAddress())
                        .estimatedCost(place.getEstimatedCost())
                        .description(place.getDescription())
                        .gptOriginalName(place.getGptOriginalName())
                        .fromPrevious(FromPreviousDto.fromEntity(place.getFromPrevious()))
                        .build())
                .toList();
    }

    public FullScheduleResDto convertToBlockStructure(List<PlaceDetailDto> places, TravelSchedule travelSchedule) {
        int days = (int) ChronoUnit.DAYS.between(travelSchedule.getStartDate(), travelSchedule.getEndDate()) + 1;
        Map<Integer, List<PlaceDetailDto>> groupedByDayIndex = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) groupedByDayIndex.put(i, new ArrayList<>());
        for (int i = 0; i < places.size(); i++) {
            int dayIndex = i / 7;
            if (dayIndex < days) groupedByDayIndex.get(dayIndex).add(places.get(i));
        }

        List<FullScheduleResDto.DailyScheduleBlock> blocks = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate date = travelSchedule.getStartDate().plusDays(i);
            String day = (i + 1) + "일차";
            List<PlaceDetailDto> dayPlaces = groupedByDayIndex.get(i);

            int total = dayPlaces.stream()
                    .map(PlaceDetailDto::getEstimatedCost)
                    .filter(Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .sum();

            List<FullScheduleResDto.PlaceResponse> placeResponses = dayPlaces.stream()
                    .map(FullScheduleResDto.PlaceResponse::from)
                    .toList();

            blocks.add(new FullScheduleResDto.DailyScheduleBlock(day, date.toString(), total, placeResponses));
        }

        return new FullScheduleResDto(
                travelSchedule.getTitle(),
                travelSchedule.getStartDate(),
                travelSchedule.getEndDate(),
                blocks
        );
    }



    private int calculateDDay(LocalDate startDate) {
        return (int) ChronoUnit.DAYS.between(LocalDate.now(), startDate);
    }

    private String formatDday(int d) {
        if (d == 0) return "D-Day";
        return (d > 0) ? "D-" + d : "D+" + Math.abs(d);
    }
}
