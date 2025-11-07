package com.example.capstone.plan.service;

import com.example.capstone.plan.dto.request.ScheduleSaveReqDto;
import com.example.capstone.plan.dto.response.ScheduleSaveResDto;
import com.example.capstone.plan.entity.TravelDay;
import com.example.capstone.plan.entity.TravelPlace;
import com.example.capstone.plan.entity.TravelSchedule;
import com.example.capstone.plan.repository.DayRepository;
import com.example.capstone.plan.repository.PlaceRepository;
import com.example.capstone.plan.repository.ScheduleRepository;
import com.example.capstone.user.entity.UserEntity;
import com.example.capstone.user.repository.UserRepository;
import com.example.capstone.util.oauth2.dto.CustomOAuth2User;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ScheduleSaveService {

    private final DayRepository dayRepository;
    private final PlaceRepository placeRepository;
    private final UserRepository userRepository;
    private final ScheduleRepository scheduleRepository;



    @Transactional
    public ScheduleSaveResDto saveSchedule(ScheduleSaveReqDto request, CustomOAuth2User userDetails) {

        String providerId = userDetails.getProviderId();
        UserEntity user = userRepository.findByProviderId(providerId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        TravelSchedule travelSchedule = TravelSchedule.builder()
                .user(user)
                .title(request.getTitle())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .build();
        scheduleRepository.save(travelSchedule);

        LocalDate currentDate = request.getStartDate();
        for (int i = 0; i < request.getDays().size(); i++) {
            ScheduleSaveReqDto.DayRequest dayRequest = request.getDays().get(i);
            String dayLabel = (i + 1) + "일차";

            TravelDay travelDay = TravelDay.builder()
                    .travelSchedule(travelSchedule)
                    .dayNumber(i + 1)
                    .date(currentDate.toString())
                    .day(dayLabel)
                    .build();
            dayRepository.save(travelDay);

            List<ScheduleSaveReqDto.PlaceRequest> placeRequests = dayRequest.getPlaces();
            for (int j = 0; j < placeRequests.size(); j++) {
                ScheduleSaveReqDto.PlaceRequest placeRequest = placeRequests.get(j);
                TravelPlace travelPlace = TravelPlace.builder()
                        .travelDay(travelDay)
                        .name(placeRequest.getName())
                        .type(placeRequest.getType())
                        .lat(placeRequest.getLat())
                        .lng(placeRequest.getLng())
                        .estimatedCost(placeRequest.getEstimatedCost())
                        .hashtag(placeRequest.getHashtag())
                        .placeOrder(j)
                        .walkTime(placeRequest.getWalkTime())
                        .driveTime(placeRequest.getDriveTime())
                        .transitTime(placeRequest.getTransitTime())
                        .build();
                placeRepository.save(travelPlace);
            }
            currentDate = currentDate.plusDays(1);
        }

        return new ScheduleSaveResDto(travelSchedule.getId());
    }
}
