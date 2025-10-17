package com.example.capstone.util.chatbot;

import com.example.capstone.plan.entity.City;
import org.springframework.stereotype.Component;

@Component
public class SpotPromptBuilder {

    private static final int DEFAULT_COUNT = 3;

    /* ---- 도시 기반 ---- */
    public String build(City city) {
        return build(city, DEFAULT_COUNT);
    }

    public String build(City city, int count) {
        if (city == null) throw new IllegalArgumentException("City는 필수입니다.");
        String locationLine = String.format("[%s] 지역 여행자에게 추천할 관광지 %d곳을 알려줘.", city.getDisplayName(), count);
        return basePrompt(locationLine, count);
    }

    /* ---- GPS 기반 ---- */
    public String build(double latitude, double longitude) {
        return build(latitude, longitude, DEFAULT_COUNT);
    }

    public String build(double latitude, double longitude, int count) {
        String locationLine = String.format("사용자의 현재 위치는 위도 %.6f, 경도 %.6f야. 이 근처의 관광지 %d곳을 추천해줘.", latitude, longitude, count);
        return basePrompt(locationLine, count);
    }

    /* ---- 공통 프롬프트 ---- */
    private String basePrompt(String locationLine, int count) {
        return String.format("""
[시스템 역할]
너는 대한민국 관광지 정보를 제공하는 여행 가이드이며, 한국어로만 답한다.

[요청]
%s

[출력 규칙]
- 반드시 JSON 배열([]) 한 개만 출력한다. 마크다운/설명/문장 금지.
- 항목 수는 정확히 %d개.
- 모든 장소는 실제 존재하는 공식 명칭만 사용한다.
- 동일/유사 명칭은 중복으로 간주하므로 금지 (예: "한라산"과 "한라산 국립공원").
- 감성어·조합 명칭·연결어 포함 금지 (예: "~힐링", "및", "/", "-").
- 주소는 가능하면 도로명주소로 작성한다.
- 정보가 불확실한 항목은 제외하고 다른 확실한 장소로 대체한다.
- 이모지/특수문자 금지.

[반환 형식(JSON Array)]
[
  {
    "name": "장소 공식 명칭",
    "description": "한 줄 소개(최대 60자)",
    "hours": "예: 09:00~18:00 / 상시개방",
    "fee": "예: 무료 / 성인 6,000원, 청소년 4,000원",
    "location": "도로명주소 또는 행정주소"
  },
  {
    "name": "...",
    "description": "...",
    "hours": "...",
    "fee": "...",
    "location": "..."
  },
  {
    "name": "...",
    "description": "...",
    "hours": "...",
    "fee": "...",
    "location": "..."
  }
]
※ 위 블록은 형식 예시일 뿐이며, 최종 출력에는 예시 설명 없이 JSON 배열만 출력한다.
""", locationLine, count);
    }
}
