package com.example.capstone.util.gpt;

import com.example.capstone.plan.entity.City;
import com.example.capstone.user.entity.MBTI;
import com.example.capstone.plan.entity.PeopleGroup;
import com.example.capstone.matching.entity.TravelStyle;
import com.example.capstone.plan.dto.request.ScheduleCreateReqDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
public class GptCreatePromptBuilder {

    public String build(ScheduleCreateReqDto request) {
        StringBuilder sb = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일");
        String formattedStart = request.getStartDate().format(formatter);
        String formattedEnd = request.getEndDate().format(formatter);

        boolean isDomestic = request.getDestination() == City.NONE;
        String destinationText = isDomestic ? "국내" : request.getDestination().getDisplayName();

        sb.append(String.format("%s부터 %s까지 %s 여행 일정입니다.", formattedStart, formattedEnd, destinationText));

        if (isDomestic) {
            sb.append("""
- 목적지 '국내'일 경우, 계절/MBTI/여행성향/예산/인원 정보를 고려하여 GPT가 적절한 실제 대한민국 도시 중 하나를 **자유롭게 한 곳만 선택**하여 일정을 구성해야 해.
""");
        }

        if (request.getMbti() != MBTI.NONE) sb.append("- MBTI: ").append(request.getMbti()).append("\n");
        if (request.getTravelStyle() != TravelStyle.NONE)
            sb.append("- 여행 성향: ").append(request.getTravelStyle()).append("\n");
        if (request.getPeopleGroup() != PeopleGroup.NONE)
            sb.append("- 여행 인원: ").append(request.getPeopleGroup()).append("명\n");
        if (request.getBudget() != null) sb.append("- 예산: ").append(request.getBudget()).append("원\n");

        sb.append("""
[반드시 지켜야 할 규칙]
- 하루 7개 항목(아침, 관광지, 점심, 관광지, 저녁, 관광지, 숙소)로 구성. 누락, 순서 변경, 추가 불가.

[장소 이름 생성 규칙]

**관광지 명칭 규칙 (엄격 적용)**
- 반드시 **실제로 존재하며, 공식적으로 등록된 명소**만 작성할 것
- 장소명은 **공식 명칭 그대로** 작성해야 하며, **단어 하나라도 다르면 전체 응답은 무효**
- "공식 명칭"이란 다음과 같은 출처에서 실제 확인 가능한 명칭을 말함:
- 지자체 공식 관광 홈페이지
- 대한민국 구석구석 (한국관광공사)
- 도로 표지판, 안내판, 정부 관광 안내서 등

- 다음과 같은 표현이 포함된 명칭은 **단 1건이라도 포함 시 전체 응답 무효**:
1. 감성어: 힐링, 감성, 치유, 고요한, 여유로운, 아름다운 등
2. 추상어 또는 주제어: 생태, 체험, 테마, 역사문화, 전통문화 등
3. 허구/조합 명칭: ○○예술힐링센터, ○○체험전시관, ○○복합문화마을 등
4. 연결어 포함 명칭: ‘및’, ‘그리고’, ‘/’, ‘-’, ‘&’, ‘~’ 등
5. 두 장소 결합 표현: '○○공원 및 전망대', '○○사 & 박물관' 등

- **약간이라도 창작하거나 가공된 장소명은 무조건 무효**
- 실제 존재하지만 명칭이 다르다면 그것도 무효
- 이름 뒤에 “전통”, “체험”, “힐링” 등을 붙여 변형하는 것 금지

- **장소의 이름에 수식어나 설명어를 붙이지 말 것**
- “유명한 ○○”, “조용한 ○○”, “도심 속 ○○”, “힐링 가능한 ○○” 등 금지

- **절대 유사 명칭을 추측하여 만들어선 안 됨**

- **관광지가 부족한 경우**, 인접 시/군/구의 **공식 명소**를 포함할 수 있다.
- 단, 해당 명소도 위 조건을 전부 만족해야 한다

- 계절에 따라 적절한 장소만 작성
- 봄: 꽃축제, 정원, 도심 공원
- 여름: 해수욕장, 실내 관광지, 동굴
- 가을: 단풍 명소, 고궁, 유적지
- 겨울: 눈꽃 명소, 온천, 스키장

 **숙소 명칭 규칙 (엄격 적용)** 
- 형식: `"지역 + 업종"` 또는 `"지역 + 동/읍/면 + 업종"` 
  - 예시: 제주시 호텔, 서귀포시 리조트, 제주시 성산면 호텔 
- ❌ 아래 항목은 절대 포함하면 안 됨: 
  - 감성 표현 (예: 힐링, 고급, 감성, 뷰 좋은 등) 
  - 브랜드명 (예: 라마다, 신라스테이 등)

**식사 명칭 규칙 (엄격 적용)**
1. 식사명은 정확히 다음 형식으로 작성해야 합니다:
   - "지역 + 음식종류 + 맛집"
   - 예시: "제주시 전복죽 맛집", "대구시 막창 맛집"
2. 음식 종류는 반드시 하나의 단어로 구성된, 일반적인 표현이어야 합니다:
   - 예: 냉면, 회, 삼겹살, 곰탕, 갈비, 칼국수
3. 다음과 같은 표현은 절대 포함하지 마세요:
   - 감성어: 현지인 추천, 전통, 브런치, 디저트, 조식 등
   - 시간대 표현: 아침국수, 점심정식, 야식 등
   - 조합형 표현: 매실갈비, 한방삼계탕, 허브족발 등
   - 복합 수식어 또는 설명어: 건강식, 퓨전요리, 한식뷔페 등
4. 음식 종류는 해당 지역의 특산물 또는 일반적으로 잘 알려진 음식 중 하나여야 합니다:
   - 예: 제주 → 흑돼지, 전복죽 / 속초 → 물회, 오징어순대 / 전주 → 콩나물국밥
   - 잘 모를 경우 삼겹살, 곰탕, 냉면, 칼국수 등 전국적으로 익숙한 일반 음식 사용 가능
❌ 위 조건 중 하나라도 어기면 전체 응답은 무효입니다.

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