package com.example.capstone.chatbot.service;

import com.example.capstone.chatbot.dto.response.WeatherResDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;


@Component
@RequiredArgsConstructor
public class OpenWeatherClient {

    @Value("${openweather.api.key}")
    private String apiKey;

    private final RestTemplateBuilder restTemplateBuilder;
    private final ObjectMapper objectMapper;

    private RestTemplate getRestTemplate() {
        return restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    public WeatherResDto getWeather(double lat, double lon, String regionName) {
        String url = UriComponentsBuilder
                .fromHttpUrl("https://api.openweathermap.org/data/3.0/onecall")
                .queryParam("lat", lat)
                .queryParam("lon", lon)
                .queryParam("exclude", "minutely,hourly,alerts")
                .queryParam("appid", apiKey)
                .queryParam("units", "metric")
                .queryParam("lang", "kr")
                .toUriString();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = getRestTemplate()
                    .exchange(url, HttpMethod.GET, entity, String.class);

            JsonNode body = objectMapper.readTree(response.getBody());

            double currentTemp = body.path("current").path("temp").asDouble();
            double minTemp = body.path("daily").path(0).path("temp").path("min").asDouble();
            double maxTemp = body.path("daily").path(0).path("temp").path("max").asDouble();
            double pop = body.path("daily").path(0).path("pop").asDouble() * 100;

            String rawDescription = body.path("current")
                    .path("weather").path(0).path("description").asText();

            String simplifiedDescription = simplifyWeatherDescription(rawDescription);

            String requestTime = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

            return new WeatherResDto(
                    regionName,
                    String.format("%.1f°C", currentTemp),
                    String.format("%.1f°C", minTemp),
                    String.format("%.1f°C", maxTemp),
                    String.format("%.0f%%", pop),
                    simplifiedDescription,
                    requestTime
            );

        } catch (HttpStatusCodeException e) {
            throw new RuntimeException("OpenWeather API 호출 실패: " +
                    e.getStatusCode() + " / " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new RuntimeException("OpenWeather API 응답 처리 중 오류: " + e.getMessage(), e);
        }
    }

    private String simplifyWeatherDescription(String description) {
        if (description.contains("구름") || description.contains("흐림")) return "구름";
        if (description.contains("비")) return "비";
        if (description.contains("눈")) return "눈";
        if (description.contains("맑")) return "맑음";
        return "기타";
    }
}
