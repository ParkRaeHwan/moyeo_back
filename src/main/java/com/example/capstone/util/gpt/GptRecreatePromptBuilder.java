package com.example.capstone.util.gpt;

import com.example.capstone.plan.dto.request.ScheduleCreateReqDto;
import com.example.capstone.plan.entity.City;
import com.example.capstone.user.entity.MBTI;
import com.example.capstone.plan.entity.PeopleGroup;
import com.example.capstone.matching.entity.TravelStyle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
public class GptRecreatePromptBuilder {

    public String build(ScheduleCreateReqDto request, List<String> excludedNames) {
        StringBuilder sb = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일");
        String formattedStart = request.getStartDate().format(formatter);
        String formattedEnd = request.getEndDate().format(formatter);

        boolean isDomestic = request.getDestination() == City.NONE;
        String destinationText = isDomestic ? "국내" : request.getDestination().getDisplayName();

        sb.append(String.format("%s부터 %s까지 %s 여행 일정을 **다시 추천**해주세요.\n", formattedStart, formattedEnd, destinationText));
        sb.append("단, 아래 조건을 반드시 지켜야 합니다:\n\n");

        if (isDomestic) {
            sb.append("""
- 목적지 '국내'일 경우, 계절/MBTI/여행성향/예산/인원 정보를 고려하여 GPT가 적절한 실제 대한민국 도시 중 하나를 **자유롭게 한 곳만 선택**하여 일정을 구성해야 합니다.
- 단, 하루 또는 전체 일정에서 **두 개 이상의 도시가 섞이면 절대 안 됩니다.**
- 반드시 **선택된 하나의 도시 내에서만** 전 일정(아침~숙소)을 구성하세요.

""");
        }

        if (request.getMbti() != MBTI.NONE) sb.append("- MBTI: ").append(request.getMbti()).append("\n");
        if (request.getTravelStyle() != TravelStyle.NONE) sb.append("- 여행 성향: ").append(request.getTravelStyle()).append("\n");
        if (request.getPeopleGroup() != PeopleGroup.NONE) sb.append("- 여행 인원: ").append(request.getPeopleGroup()).append("명");
        if (request.getBudget() != null) sb.append("- 예산: ").append(request.getBudget()).append("원");

        if (excludedNames != null && !excludedNames.isEmpty()) {
            sb.append("""
❗❗ 아래 장소는 과거 일정에서 사용된 장소이므로, 반드시 **전체 일정에서 단 1회도 등장하면 안 됩니다.**
❗❗ 아래 장소 중 **하나라도 일정에 포함될 경우, 전체 응답은 무효 처리됩니다.**
❗❗ 장소명은 단어 순서를 바꾸거나 일부를 변형해도 금지입니다.
❗❗ 동일하거나 유사한 표현도 포함하면 안 됩니다.

""");
            for (String name : excludedNames) {
                sb.append("- ").append(name).append("\n");
            }
        }

        sb.append("""

[반드시 지켜야 할 규칙]
- 하루 7개 항목(아침, 관광지, 점심, 관광지, 저녁, 관광지, 숙소)로 구성. 누락, 순서 변경, 추가 불가.

[장소 이름 생성 규칙]

 **숙소 명칭 규칙 (엄격 적용)** 
- 형식: `"지역 + 업종"` 또는 `"지역 + 동/읍/면 + 업종"` 
  - 예시: 제주시 호텔, 서귀포시 리조트, 제주시 성산면 호텔 
- ❌ 아래 항목은 절대 포함하면 안 됨: 
  - 감성 표현 (예: 힐링, 고급, 감성, 뷰 좋은 등) 
  - 브랜드명 (예: 라마다, 신라스테이 등)

 **식사 명칭 규칙 (엄격 적용)** 
1. 식사명 형식은 정확히 `"지역 + 음식종류 + 맛집"` 으로만 작성하세요.
 - ✅ 예: "제주시 전복죽 맛집", "대구시 막창 맛집"
 - ❌ 감성 수식어 포함 금지: "현지인 추천", "전통", "브런치", "디저트", "조식" 등
 - ❌ 형식 위반 금지: "여수 아침국수 맛집" → 시간대 표현 불가
2. 음식종류는 **해당 지역에서 잘 알려진 특산물 또는 향토 음식**으로 선택하세요.
 - 예: 제주 → 흑돼지, 전복죽 / 속초 → 물회, 오징어순대 / 전주 → 콩나물국밥 등
 - GPT가 모르는 경우, 해당 지역에서 가장 흔히 알려진 일반 음식으로 작성해도 됩니다 (예: 삼겹살, 회 등)
3. 음식 종류는 한국인이 일상적으로 사용하는 일반적인 표현으로만 사용하세요.
 - ✅ 예: 냉면, 회, 삼겹살, 곰탕, 갈비, 칼국수 등
 - ❌ 예: 한식뷔페, 건강식, 간식, 퓨전요리 등은 금지

 **관광지 명칭 규칙 (엄격 적용)** 
1. 반드시 **실제로 존재하는 명소**만 포함 (뉴스, 블로그, 여행책자 등에 자주 등장하는 곳)
2. **장소명은 반드시 공식 명칭 그대로 작성해야 하며, 단어 한 글자도 다르면 전체 응답은 무효입니다.**
- ✅ 반드시 **공식 표기명** 그대로만 작성하세요.
예: '망우역사문화공원' (O) → '망우산유물전시관'(X)
- ✅ 검색 가능한 명칭이더라도 **정식 명칭이 아니면 금지**입니다.
- ✅ 정부, 지자체, 공공기관, 공식 관광 안내서, 도로 표지판 등에 등록된 **정확한 명칭만 허용**
- ❌ 다음과 같은 표현이 포함된 이름은 생성 금지:
- 감성어: 힐링, 감성, 도심속, 치유, 고요한 등
- 주제어: 생태, 천문, 자연사, 우주, 체험, 테마, 역사문화 등
- 조합/허구 예시: 서울도심속미술관, ○○예술힐링센터, ○○유물전시관, 안정리예술의거리 등
3. 관광지가 부족하면 **1시간 이내 인접 시/군/구의 실제 명소**는 허용
(예: 해남군 → 완도군, 진도군, 강진군)
4. 그래도 부족하면 아래 형식의 **공공 fallback 장소**만 허용:
- 형식: "지역명 + 장소유형" (예: 정읍시 미술관)
- 허용 유형: 박물관, 미술관, 향교, 공원, 전망대, 전통시장, 역사관
- 금지 유형: 자연사박물관, 천문과학관, 생태체험센터 등
5. 모든 장소는 **계절에 맞아야 함**
- 봄: 꽃축제, 공원 등 / 여름: 해수욕장, 실내 관광지 / 가을: 단풍, 고궁 / 겨울: 눈꽃, 온천
🔴 위 조건 중 하나라도 어기면 전체 응답은 무효

[JSON 형식 규칙]
- `location` 필드는 관광지에만 포함 (lat/lng는 null 가능).
- `식사`, `숙소` 항목에는 `location` 필드 포함 금지.
- `description`은 반드시 포함하되 빈 문자열로 둘 것 (예: "description": "")
- JSON은 key-value 쌍만 포함하며, 계층 구조 오류나 오타 발생 시 전체 무효.

[중복 방지 조건]
- `name` 기준으로 전체 일정 내 장소 중복 금지.
- 날짜가 달라도 동일 장소/유사 표현 재등장 금지.
예시
{
  "itinerary": [
    {
      "date": "2025-08-10",
      "travelSchedule": [
        { "type": "식사", "name": "해남군 전복죽 맛집" },
        { "type": "관광지", "name": "해남공룡박물관" },
        { "type": "식사", "name": "해남군 삼계탕 맛집" },
        { "type": "관광지", "name": "두륜산케이블카" },
        { "type": "식사", "name": "해남군 곰탕 맛집" },
        { "type": "관광지", "name": "해남군 미술관" },
        { "type": "숙소", "name": "해남군 호텔" }
      ]
    }
  ]
}

""");

        return sb.toString();
    }
}
