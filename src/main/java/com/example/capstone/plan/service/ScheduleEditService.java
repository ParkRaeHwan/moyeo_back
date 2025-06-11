package com.example.capstone.plan.service;

import com.example.capstone.plan.dto.common.FromPreviousDto;
import com.example.capstone.plan.dto.common.KakaoPlaceDto;
import com.example.capstone.plan.dto.response.FullScheduleResDto.PlaceResponse;
import com.example.capstone.plan.dto.response.ScheduleEditResDto;
import com.example.capstone.util.gpt.GptEditPromptBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleEditService {

    private final GptEditPromptBuilder promptBuilder;
    private final OpenAiClient openAiClient;
    private final KakaoMapClient kakaoMapClient;
    private final ObjectMapper objectMapper;

    public ScheduleEditResDto Edit(List<String> names) throws Exception {
        String prompt = promptBuilder.build(names);
        String gptResponse = openAiClient.callGpt(prompt);
        List<PlaceResponse> places = parseGptResponse(gptResponse);

        int total = places.stream().mapToInt(PlaceResponse::getEstimatedCost).sum();

        return ScheduleEditResDto.builder()
                .totalEstimatedCost(total)
                .places(places)
                .build();
    }


    private List<PlaceResponse> parseGptResponse(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);

        JsonNode placesNode;
        if (root.isArray()) {
            // 과거 형식 유지 대응 (fallback)
            placesNode = root;
        } else {
            placesNode = root.path("places");
            if (!placesNode.isArray()) {
                throw new IllegalArgumentException("GPT 응답 내 'places' 필드는 JSON 배열이어야 합니다.");
            }
        }

        List<PlaceResponse> result = new ArrayList<>();
        for (JsonNode node : placesNode) {
            String name = node.path("name").asText();
            String gptOriginalName = node.path("gptOriginalName").asText();

            KakaoPlaceDto kakao = kakaoMapClient.searchPlace(name);

            if (kakao == null) continue;

            String matchedName = kakao.getPlaceName();
            if (!matchedName.contains(name) && !name.contains(matchedName)) continue;

            PlaceResponse place = PlaceResponse.builder()
                    .name(name)
                    .gptOriginalName(gptOriginalName)
                    .type(node.path("type").asText())
                    .estimatedCost(node.path("estimatedCost").asInt())
                    .description(node.path("description").asText())
                    .fromPrevious(node.has("fromPrevious") ? new FromPreviousDto(
                            node.get("fromPrevious").path("walk").asInt(0),
                            node.get("fromPrevious").path("publicTransport").asInt(0),
                            node.get("fromPrevious").path("car").asInt(0)
                    ) : null)
                    .address(kakao.getAddress())
                    .lat(kakao.getLatitude())
                    .lng(kakao.getLongitude())
                    .build();

            result.add(place);
        }

        return result;
    }

}
