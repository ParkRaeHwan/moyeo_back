package com.example.capstone.plan.controller;

import com.example.capstone.plan.dto.request.*;
import com.example.capstone.plan.dto.response.*;
import com.example.capstone.plan.service.*;
import com.example.capstone.util.oauth2.dto.CustomOAuth2User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@RestController
@RequestMapping("/schedule")
@RequiredArgsConstructor
@Tag(name = "Schedule API", description = "여행 일정 생성, 수정, 저장 관련 API")
public class ScheduleController {

    private final ScheduleEditService scheduleEditService;
    private final ScheduleSaveService scheduleSaveService;
    private final ScheduleRecreateService scheduleRecreateService;
    private final ScheduleCreateService scheduleCreateService;
    private final PlaceDetailService placeDetailService;
    private final ScheduleDeleteService scheduleDeleteService;
    private final ScheduleQueryService scheduleQueryService;
    private final ScheduleResaveService scheduleResaveService;




    @Operation(summary = "스케줄 상세조회", description = "저장된 스케줄 ID로 전체 일정을 조회합니다.")
    @GetMapping("/full/{scheduleId}")
    public ResponseEntity<ScheduleCreateResDto> getFullDetail(
            @AuthenticationPrincipal CustomOAuth2User userDetails,
            @PathVariable Long scheduleId) {

        ScheduleCreateResDto response = scheduleQueryService.getFullSchedule(scheduleId, userDetails);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "내 여행 목록 조회", description = "로그인한 사용자가 생성한 여행 일정을 리스트로 조회합니다.")
    @GetMapping("/list")
    public ResponseEntity<List<SimpleScheduleResDto>> getScheduleList(
            @AuthenticationPrincipal CustomOAuth2User userDetails) {

        List<SimpleScheduleResDto> response = scheduleQueryService.getSimpleScheduleList(userDetails);
        return ResponseEntity.ok(response);
    }



    @Operation(summary = "추천 일정 저장", description = "사용자가 확정한 여행 일정을 데이터베이스에 저장합니다.")
    @PostMapping("/save")
    public ResponseEntity<ScheduleSaveResDto> saveSchedule(
            @AuthenticationPrincipal CustomOAuth2User userDetails,
            @RequestBody ScheduleSaveReqDto request) {

        ScheduleSaveResDto response = scheduleSaveService.saveSchedule(request, userDetails);
        return ResponseEntity.ok(response);
    }
    @Operation(summary = "기존 일정 덮어쓰기 저장", description = "수정된 여행 일정을 기존 일정 ID에 덮어씁니다.")
    @PostMapping("/resave/{scheduleId}")
    public ResponseEntity<ScheduleSaveResDto> resaveDay(
            @PathVariable Long scheduleId,
            @RequestBody ScheduleResaveReqDto request,
            @AuthenticationPrincipal CustomOAuth2User userDetails) {

        ScheduleSaveResDto response = scheduleResaveService.resaveDayPlaces(scheduleId, request, userDetails);
        return ResponseEntity.ok(response);
    }


    @Operation(summary = "일정 수정", description = "수정된 장소 리스트를 기반으로 하루 일정을 리빌딩합니다.")
    @PostMapping("/edit")
    public ResponseEntity<ScheduleEditResDto> rebuildDay(@RequestBody ScheduleEditReqDto request) {
        ScheduleEditResDto result = scheduleEditService.editSchedule(request.getNames());
        return ResponseEntity.ok(result);
    }


    @Operation(summary = "일정 삭제", description = "저장된 사용자의 여행 일정 삭제")
    @DeleteMapping("/delete/{scheduleId}")
    public ResponseEntity<?> deleteSchedule(@PathVariable Long scheduleId,
                                            @AuthenticationPrincipal CustomOAuth2User userDetails) {
        scheduleDeleteService.deleteSchedule(scheduleId, userDetails);
        return new ResponseEntity<>(HttpStatus.OK);
    }
    @Operation(summary = "GPT 기반 여행일정 생성", description = "MBTI, 여행 성향, 예산 등을 기반으로 여행 일정을 생성합니다.")
    @PostMapping("/create")
    public ResponseEntity<ScheduleCreateResDto> createSchedule(@RequestBody ScheduleCreateReqDto request) {
        ScheduleCreateResDto response = scheduleCreateService.generateSchedule(request);
        return ResponseEntity.ok(response);
    }
    @Operation(summary = "장소 상세정보 조회", description = "장소 이름과 타입, 예산을 기반으로 한줄 소개, 주소, 좌표를 포함한 상세정보를 반환합니다.")
    @PostMapping("/detail")
    public ResponseEntity<PlaceDetailResDto> getPlaceDetail(@RequestBody PlaceDetailReqDto request) {
        PlaceDetailResDto response = placeDetailService.getPlaceDetail(request);
        return ResponseEntity.ok(response);
    }
    @Operation(summary = "기존 일정을 제외한 일정 재생성", description = "create로 받은 일정에서 장소들을 제외하고 새로운 일정을 생성합니다.")
    @PostMapping("/recreate")
    public ResponseEntity<ScheduleCreateResDto> regenerate(@RequestBody ScheduleRecreateReqDto request) {
        ScheduleCreateResDto response = scheduleRecreateService.recreateSchedule(request);
        return ResponseEntity.ok(response);
    }

}

