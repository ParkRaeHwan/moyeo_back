package com.example.capstone.util.gpt;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GptEditPromptBuilder {

    public String build(List<String> names) {
        StringBuilder sb = new StringBuilder();

        sb.append("""
            다음은 편집된 여행 장소명 목록이야. 각 항목에 대해 아래 스키마의 JSON만 반환해.
            출력은 반드시 평문 JSON 하나만, 코드펜스/설명/주석 금지.

            출력 스키마:
            {
              "places": [
                {
                  "name": "<입력 그대로>",
                  "type": "식사|숙소|관광지|액티비티",
                  "estimatedCost": <정수(원)>,
                  "description": "<20자 내외 한 줄>",
                  "hashtag": "<지역+업종 기반 해시태그형 설명>"
                }
              ]
            }

            규칙:
            - 입력 순서를 유지하고, 입력 개수와 동일한 개수 생성.
            - 숫자 필드는 정수만(기호/단위/쉼표 금지).
            - description, gptOriginalName은 한국어 문장. 나머지는 스키마 그대로.
            - 추가 텍스트 없이 JSON만 출력.

            입력 장소:
            """);

        for (String name : names) {
            sb.append("- ").append(name).append("\n");
        }

        return sb.toString();
    }
}
