package com.example.capstone.plan.service;

import com.example.capstone.plan.dto.request.ScheduleResaveReqDto;
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

import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleResaveService {

    private final ScheduleRepository scheduleRepository;
    private final DayRepository dayRepository;
    private final PlaceRepository placeRepository;
    private final UserRepository userRepository;

    @Transactional
    public ScheduleSaveResDto resaveDayPlaces(Long scheduleId, ScheduleResaveReqDto request, CustomOAuth2User userDetails) {

        // ✅ 1. 사용자 검증
        String providerId = userDetails.getProviderId();
        UserEntity user = userRepository.findByProviderId(providerId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // ✅ 2. 일정 검증
        TravelSchedule schedule = scheduleRepository.findByIdAndUserId(scheduleId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("해당 일정이 존재하지 않거나 접근 권한이 없습니다."));

        // ✅ 3. 요청에서 하루 일정 추출
        ScheduleResaveReqDto.DayRequest dayReq = request.getDays().get(0);

        // ✅ 4. 수정 대상 Day 조회 ("1일차" 기준)
        List<TravelDay> days = dayRepository.findAllByTravelScheduleId(scheduleId);
        TravelDay targetDay = days.stream()
                .filter(d -> d.getDay().equals(dayReq.getDay()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(dayReq.getDay() + "을(를) 찾을 수 없습니다."));

        // ✅ 5. 기존 Place 전체 삭제 (Day는 유지)
        placeRepository.deleteAllByTravelDayId(targetDay.getId());

        // ✅ 6. 새로운 Place 저장
        for (int i = 0; i < dayReq.getPlaces().size(); i++) {
            var p = dayReq.getPlaces().get(i);

            TravelPlace newPlace = TravelPlace.builder()
                    .travelDay(targetDay)
                    .type(p.getType())
                    .name(p.getName())
                    .hashtag(p.getHashtag())
                    .estimatedCost(p.getEstimatedCost())
                    .lat(p.getLat())
                    .lng(p.getLng())
                    .placeOrder(i)
                    .walkTime(p.getWalkTime())
                    .driveTime(p.getDriveTime())
                    .transitTime(p.getTransitTime())
                    .build();

            placeRepository.save(newPlace);
        }

        return new ScheduleSaveResDto(schedule.getId());
    }
}
