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

    @Value("${gemini.model:gemini-1.5-pro}")
    private String model;

    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    private WebClient getWebClient() {
        return webClientBuilder
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .defaultHeader("x-goog-api-key", apiKey) // Gemini는 Bearer 대신 API Key 헤더
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /** 프롬프트를 보내고 Gemini가 반환한 첫 번째 후보 text만 추출 */
    public String callGemini(String prompt) {
        try {
            // 요청 JSON
            String requestBody = objectMapper.writeValueAsString(
                    Map.of(
                            "contents", new Object[]{
                                    Map.of(
                                            "parts", new Object[]{
                                                    Map.of("text", prompt)
                                            }
                                    )
                            }
                    )
            );

            // API 호출
            String responseBody = getWebClient()
                    .post()
                    .uri("/models/" + model + ":generateContent")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // 응답 파싱
            JsonNode root = objectMapper.readTree(responseBody);

            // candidates[0].content.parts[0].text 꺼내기
            return root.path("candidates")
                    .get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text")
                    .asText();

        } catch (Exception e) {
            throw new RuntimeException("Gemini 응답 처리 중 오류", e);
        }
    }
}
