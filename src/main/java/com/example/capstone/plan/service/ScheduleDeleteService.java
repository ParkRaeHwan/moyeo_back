package com.example.capstone.plan.service;

import com.example.capstone.plan.entity.TravelSchedule;
import com.example.capstone.plan.repository.ScheduleRepository;
import com.example.capstone.user.entity.UserEntity;
import com.example.capstone.user.repository.UserRepository;
import com.example.capstone.util.oauth2.dto.CustomOAuth2User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ScheduleDeleteService {

    private final ScheduleRepository scheduleRepository;
    private final UserRepository userRepository;

    public void deleteSchedule(Long scheduleId, CustomOAuth2User userDetails) {
        UserEntity user = userRepository.findByProviderId(userDetails.getProviderId())
                .orElseThrow(() -> new EntityNotFoundException("User Not Found"));

        TravelSchedule schedule = scheduleRepository.findByIdAndUserId(scheduleId, user.getId())
                .orElseThrow(() -> new EntityNotFoundException("Schedule Not Found"));

        scheduleRepository.delete(schedule);
    }
}
