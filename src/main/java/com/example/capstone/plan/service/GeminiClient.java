package com.example.capstone.plan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class GeminiClient {

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.5-flash-lite}")
    private String model;

    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    private WebClient getWebClient() {
        return webClientBuilder
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 프롬프트를 보내고 Gemini가 반환한 JSON 문자열을 그대로 돌려줌.
     * - generationConfig.responseMimeType = "application/json" 으로 강제
     * - 만약 모델이 코드펜스/백틱을 붙여도 JSON만 추출해서 반환
     */
    public String callGemini(String prompt) {
        try {
            // 요청 JSON (JSON 응답 강제)
            String requestBody = objectMapper.writeValueAsString(
                    Map.of(
                            "contents", new Object[]{
                                    Map.of("parts", new Object[]{
                                            Map.of("text", prompt)
                                    })
                            },
                            "generationConfig", Map.of(
                                    "responseMimeType", "application/json",
                                    "temperature", 0.2 // 필요 시 조정
                            )
                    )
            );

            // API 호출 (권장: key를 쿼리스트링에)
            String responseBody = getWebClient()
                    .post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/models/" + model + ":generateContent")
                            .queryParam("key", apiKey)
                            .build())
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // 응답 파싱
            JsonNode root = objectMapper.readTree(responseBody);

            // 에러/차단 케이스(선호도 피드백) 체크
            JsonNode promptFeedback = root.path("promptFeedback");
            if (!promptFeedback.isMissingNode()) {
                String blockReason = promptFeedback.path("blockReason").asText(null);
                if (blockReason != null && !blockReason.isBlank()) {
                    throw new RuntimeException("Gemini가 요청을 차단했습니다. blockReason=" + blockReason);
                }
            }

            // 기본 추출 경로: candidates[0].content.parts[0].text (JSON 텍스트 기대)
            String text = root.path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text")
                    .asText(null);

            if (text == null) {
                // 혹시 다른 파트 구조일 수 있어 백업 탐색
                for (JsonNode cand : root.path("candidates")) {
                    for (JsonNode part : cand.path("content").path("parts")) {
                        if (part.has("text")) {
                            text = part.get("text").asText();
                            break;
                        }
                    }
                    if (text != null) break;
                }
            }

            if (text == null) {
                throw new RuntimeException("Gemini 응답에서 text를 찾을 수 없습니다: " + responseBody);
            }

            // 혹시 코드펜스/백틱/앞뒤 잡텍스트가 껴 있어도 JSON 블록만 추출
            return extractJsonBlock(text);

        } catch (Exception e) {
            throw new RuntimeException("Gemini 응답 처리 중 오류", e);
        }
    }

    /**
     * 바로 Jackson으로 파싱해서 JsonNode로 받고 싶은 경우 사용.
     * (위 callGemini()에서 정리된 JSON 문자열을 파싱)
     */
    public JsonNode callGeminiAsJsonNode(String prompt) {
        try {
            String json = callGemini(prompt);
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("Gemini JSON 파싱 실패", e);
        }
    }

    /**
     * LLM 응답에서 코드펜스/백틱을 제거하고, {...} 또는 [...] JSON만 잘라 반환
     */
    private String extractJsonBlock(String raw) {
        if (raw == null) return null;
        String s = raw.trim();

        // ```json / ``` 코드펜스 제거
        s = s.replaceAll("^```(?:json)?\\s*", "");
        s = s.replaceAll("\\s*```\\s*$", "");

        // 앞뒤 백틱들 제거
        s = s.replaceAll("^`+", "");
        s = s.replaceAll("`+$", "");

        // JSON 시작/끝만 추출
        int objStart = s.indexOf('{');
        int arrStart = s.indexOf('[');
        int start;
        if (objStart >= 0 && arrStart >= 0) start = Math.min(objStart, arrStart);
        else if (objStart >= 0) start = objStart;
        else start = arrStart; // -1일 수도

        if (start < 0) return s; // 그래도 반환(상위에서 파싱 실패 시 로깅에 도움)

        int objEnd = s.lastIndexOf('}');
        int arrEnd = s.lastIndexOf(']');
        int end = Math.max(objEnd, arrEnd);

        if (end >= start) {
            return s.substring(start, end + 1).trim();
        }
        return s;
    }
}
