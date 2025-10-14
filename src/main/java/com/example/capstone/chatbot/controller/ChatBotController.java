package com.example.capstone.chatbot.controller;

import com.example.capstone.chatbot.dto.request.*;
import com.example.capstone.chatbot.entity.ChatCategory;
import com.example.capstone.chatbot.service.*;
import com.example.capstone.plan.entity.City;
import com.example.capstone.util.chatbot.*;
import com.example.capstone.util.chatbot.recreate.FestivalRecreatePromptBuilder;
import com.example.capstone.util.chatbot.recreate.SpotRecreatePromptBuilder;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/chatbot")
@RequiredArgsConstructor
public class ChatBotController {

    private final DestinationChatService destinationChatService;
    private final GpsChatService gpsChatService;
    private final DestinationChatRecreateService destinationChatRecreateService;
    private final GpsChatRecreateService gpsChatRecreateService;

    @Operation(summary = "목적지 기반 추천", description = "선택한 목적지를 기반으로 관광지, 축제, 음식점, 숙소, 날씨를 추천합니다.")
    @PostMapping("/destination")
    public ResponseEntity<?> getInfoByDestination(@RequestBody ChatBotReqDto request) throws Exception {
        City city = request.getCity();
        ChatCategory category = request.getCategory();

        return switch (category) {
            case SPOT -> ResponseEntity.ok(destinationChatService.getSpot(city));
            case FESTIVAL -> ResponseEntity.ok(destinationChatService.getFestivalList(city));
            case FOOD -> ResponseEntity.ok(destinationChatService.getFoodList(city));
            case HOTEL -> ResponseEntity.ok(destinationChatService.getHotelList(city));
            case WEATHER -> ResponseEntity.ok(destinationChatService.getWeather(city));
        };
    }

    @Operation(summary = "GPS 기반 추천", description = "현위치를 기반으로 관광지, 축제, 음식점, 숙소, 날씨를 추천합니다.")
    @PostMapping("/gps")
    public ResponseEntity<?> getInfoByLocation(@RequestBody ChatBotGpsReqDto request) throws Exception {
        double lat = request.getLatitude();
        double lng = request.getLongitude();
        ChatCategory category = request.getCategory();

        return switch (category) {
            case SPOT -> ResponseEntity.ok(gpsChatService.getSpotList(lat, lng));
            case FESTIVAL -> ResponseEntity.ok(gpsChatService.getFestivalList(lat, lng));
            case FOOD -> ResponseEntity.ok(gpsChatService.getFoodList(lat, lng));
            case HOTEL -> ResponseEntity.ok(gpsChatService.getHotelList(lat, lng));
            case WEATHER -> ResponseEntity.ok(gpsChatService.getWeather(lat, lng));
        };
    }

    @Operation(summary = "목적지 기반 재추천", description = "선택한 목적지를 기반으로 관광지, 축제, 음식점, 숙소를 재추천합니다. 제외할 장소명 리스트를 함께 보낼 수 있습니다.")
    @PostMapping("/recreate/destination")
    public ResponseEntity<?> recreateChatInfo(@RequestBody ChatBotRecreateReqDto req) {
        ChatCategory category = req.getCategory();

        return switch (category) {
            case SPOT -> ResponseEntity.ok(destinationChatRecreateService.recreateSpot(req));
            case FESTIVAL -> ResponseEntity.ok(destinationChatRecreateService.recreateFestival(req));
            case FOOD -> ResponseEntity.ok(destinationChatRecreateService.recreateFood(req));
            case HOTEL -> ResponseEntity.ok(destinationChatRecreateService.recreateHotel(req));
            default -> ResponseEntity.badRequest().body("지원하지 않는 카테고리입니다.");
        };
    }


    @Operation(summary = "GPS 기반 재추천", description = "현위치 기반으로 관광지, 축제, 음식점, 숙소를 재추천합니다. 제외할 장소명 리스트를 함께 보낼 수 있습니다.")
    @PostMapping("/recreate/gps")
    public ResponseEntity<?> recreateByGps(@RequestBody ChatBotGpsRecreateReqDto request) {
        double lat = request.getLatitude();
        double lng = request.getLongitude();
        ChatCategory category = request.getCategory();
        List<String> excluded = request.getExcludedNames();

        return switch (category) {
            case SPOT -> ResponseEntity.ok(gpsChatRecreateService.recreateSpot(lat, lng, excluded));
            case FESTIVAL -> ResponseEntity.ok(gpsChatRecreateService.recreateFestival(lat, lng, excluded));
            case FOOD -> ResponseEntity.ok(gpsChatRecreateService.recreateFood(lat, lng, excluded));
            case HOTEL -> ResponseEntity.ok(gpsChatRecreateService.recreateHotel(lat, lng, excluded));
            default -> ResponseEntity.badRequest().body("지원하지 않는 카테고리입니다.");
        };
    }



}

