package com.example.capstone.util.chatbot.recreate;

import com.example.capstone.plan.entity.City;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class SpotRecreatePromptBuilder {

    public String build(int index, City city, Double lat, Double lng, List<String> excludedNames) {
        // 제외 목록 포맷
        String excluded = (excludedNames == null || excludedNames.isEmpty())
                ? "없음"
                : excludedNames.stream()
                .map(name -> "- " + name)
                .collect(Collectors.joining("\n"));

        // 위치 정보 설명
        String locationInfo;
        if (city != null) {
            locationInfo = String.format("도시명: %s", city.getDisplayName());
        } else if (lat != null && lng != null) {
            locationInfo = String.format("사용자의 현재 위치: 위도 %.6f, 경도 %.6f", lat, lng);
        } else {
            throw new IllegalArgumentException("City 또는 위도/경도 정보가 필요합니다.");
        }

        return String.format("""
[GPT 시스템 명령]

※ 반드시 지켜야 할 출력 조건:
- JSON 배열([]) 한 개만 출력 (예: [ { ... }, { ... }, { ... } ])
- `{[` 외에 마크다운(````), 설명 문장, 안내 텍스트 절대 포함하지 말 것
- 모든 필드는 null, 빈 문자열 없이 자연스럽고 구체적인 한국어로 작성

[사용자 요청]
%s 기준으로, 아래 제외 목록에 포함된 장소들을 **모두 제외**하고
새로운 관광지 3곳을 추천해줘.

[제외할 기존 장소 목록]
%s

[제외 규칙]
- 제외 목록에 포함된 장소 및 유사 명칭은 절대 추천하지 마.
- 예: "속초해수욕장"이 목록에 있다면 "속초 해변", "속초 비치", "속초 바다" 등도 모두 제외.
- 같은 의미, 비슷한 철자, 띄어쓰기 차이 등도 동일 장소로 간주.
- 제외된 장소가 나올 경우 반드시 다른 실제 관광지로 대체.

[출력 규칙]
- 반드시 JSON 배열([]) 한 개만 출력한다. 설명/문장/마크다운 금지.
- 항목 수는 정확히 3개.
- 모든 장소는 실제 존재하는 대한민국의 공식 관광지명만 사용한다.
- 감성어·조합 명칭·연결어 포함 금지 (예: "~힐링", "감성", "및", "/", "-").
- 주소는 도로명주소 또는 행정주소로 작성.
- 불확실한 정보는 제외하고 신뢰도 높은 장소만 포함.
- 이모지·특수문자 금지.

[응답 형식(JSON Array)]
[
  {
    "name": "관광지명 (공식 명칭)",
    "description": "한 문장 소개 (최대 60자)",
    "hours": "운영시간 (예: 09:00~18:00 / 상시개방)",
    "fee": "입장료 (예: 무료 / 성인 5,000원, 청소년 3,000원)",
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

※ 반드시 위 JSON 배열 형식([ ... ])만 반환하며,
그 외 문장은 절대 포함하지 마.
""", locationInfo, excluded);
    }
}
