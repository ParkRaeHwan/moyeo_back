package com.example.capstone.util.gpt;

import com.example.capstone.plan.entity.City;
import com.example.capstone.user.entity.MBTI;
import com.example.capstone.plan.entity.PeopleGroup;
import com.example.capstone.matching.entity.TravelStyle;
import com.example.capstone.plan.dto.request.ScheduleCreateReqDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

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


        sb.append(String.format("""
다음 요구사항에 따라 여행 일정을 생성하라.

[여행 정보]
- 기간: %s ~ %s
- 목적지: %s
""", formattedStart, formattedEnd, destinationText));

        if (isDomestic) {
            sb.append("""
- 목적지 '국내'인 경우: 계절/MBTI/여행성향/예산/인원을 고려하여 **대한민국의 실제 시(市) 단위 도시 1곳만** 스스로 선택하여 전체 일정을 구성한다.
""");
        }

        if (request.getMbti() != MBTI.NONE) sb.append("- MBTI: ").append(request.getMbti()).append("\n");
        if (request.getTravelStyle() != TravelStyle.NONE) sb.append("- 여행 성향: ").append(request.getTravelStyle()).append("\n");
        if (request.getPeopleGroup() != PeopleGroup.NONE) sb.append("- 여행 인원: ").append(request.getPeopleGroup()).append("명\n");
        if (request.getBudget() != null) sb.append("- 예산: ").append(request.getBudget()).append("원\n");


        sb.append("""
        
너는 한국 국내 여행 일정을 생성하는 도우미야.
입력된 기간과 목적지 범위(시/군/구 경계 내부)에서만 장소를 선정해.

[핵심 규칙]
  1) 날짜별로 정확히 7개 항목만 생성하고, 순서를 엄격히 지킨다:
     [아침(식사) → 관광지 → 점심(식사) → 관광지 → 저녁(식사) → 관광지 → 숙소]
     - 누락/추가/순서변경 금지.
  2) 날짜는 요청의 시작일~종료일을 모두 포함하여 "date"에 YYYY-MM-DD 로 표기한다.
  3) 전체 일정에서 장소 **중복 금지**(동일/유사 명칭 포함).

  [명칭 규칙]
  [관광지(실존·공식 명칭만) 규칙]
     - 반드시 **실제로 존재**하고 **공식적으로 사용되는 정식 표기 그대로**만 사용한다.
     - 정식 표기는 다음 중 하나 이상의 **공적 출처**에서 실제로 쓰이는 표기다:
       · 지자체/기관 공식 페이지  · 한국관광공사  · 시설 간판/안내판
     - 다음이 하나라도 포함되면 **무효**:
       · 감성/추상어: 힐링, 감성, 고요한, 생태, 체험, 테마, 역사문화 등
       · 연결어/조합: ‘및’, ‘그리고’, ‘/’, ‘&’, ‘~’ 등(두 장소 결합/임의 수식)
       · 비공식 변형/별칭/축약형(예: 남산타워 ⇒ 공식: N서울타워)
     - **관할 단어(국립/도립/시립/구립)**는 **공식 표기에 실제로 포함될 때만** 사용한다.
       - 불확실하면 관할 단어를 붙이지 말고, **다른 실존 POI**를 선택한다.
     - **포괄 지명 단독 금지**(산/공원/해변/둘레길/호수/섬/거리/문화지구 등).
       - 반드시 내부의 **구체적 공식 시설명**으로 표기한다(정문·방문자센터·전망대·박물관·선착장 등).
     - **업무/생활 시설 금지**: 주차장, 사옥, 본사, 방송국, MBC, KBS, SBS, 행정복지센터, 구청, 시청, 경찰서, 우체국, 은행, 마트, 백화점, 영화관, 병원, 학교, 캠퍼스
     - **임의 관할+기관 조합 금지**: <도시명>시립미술관, <도시명>시립박물관, <도시명>도립미술관, <도시명>도립박물관, <도시명>구립미술관, <도시명>구립박물관 (예: "춘천시립미술관" 같은 추정 명칭 금지)
     - 선택된 **시/군/구 경계 내부**의 장소만 허용한다.

     [숙소 규칙]
     - 형식: "지역(시/군/구) + 업종" 또는 "지역(시/군/구) + (실제 동/읍/면명) + 업종"
       - 예: "제주시 호텔", "서귀포시 리조트", "부산 해운대구 호텔", "제주시 성산읍 호텔"
     - 허용 업종: 호텔, 리조트, 콘도, 펜션, 게스트하우스, 한옥스테이, 모텔
     - 금지: 특정 브랜드/체인명(라마다, 롯데, 신라스테이 등)과 수식/감성어(힐링, 오션뷰, 프리미엄 등)

     [식사 규칙]
     - 형식(정확히): "지역 + 음식종류 + 맛집"
       - 예: "제주시 전복죽 맛집", "대구시 막창 맛집"
     - 음식종류는 **실제 음식점 업종에서 흔히 쓰이는 '단일 음식명'**만 허용.
     - **허용 예시**: 곰탕, 삼겹살, 막창, 회, 초밥, 갈비, 닭갈비, 막국수, 칼국수, 순대, 해장국, 전복죽, 냉면, 갈치조림, 아구찜, 보쌈, 족발, 콩나물국밥, 부대찌개, 찌개, 전골, 우동, 돈까스, 파스타, 피자, 옹심이
     - **금지**:
       - 단일 식재료/농산물/가공식품(옥수수, 감자, 고구마, 토마토, 쌀, 밀, 콩 등)
       - 음료(커피, 차, 주스 등), 디저트(빙수, 케이크, 아이스크림 등), 감성어(현지인 추천, 전통, 브런치 등)
       - 시간대(아침국수, 야식 등), 조합/복합수식(매실갈비, 허브족발, 건강식, 퓨전요리 등)

     [자체 검증 체크리스트]
     - (A) 명칭이 **공적 출처에서 실제 사용되는 정식 표기**인가?
     - (B) 포괄 지명을 단독 사용하지 않았는가? → 내부 **구체적 공식 시설명**으로 표기했는가?
     - (C) 관할 단어(국립/도립/시립/구립)가 **정확히 일치**하는가? 불확실하면 제거하고 다른 실존 POI를 고르라.
     - (D) 업무/생활 시설을 관광지로 넣지 않았는가?
     - (E) 전체 일정에서 **중복 명칭**이 없는가?
     - (F) 선택된 **시/군/구 경계 내부**인가?
     - (G) 모든 날짜가 **정확히 7개 항목**이며 **지정된 순서**를 충족하는가?


[출력 스키마]
{
  "itinerary": [
    {
      "date": "YYYY-MM-DD",
      "travelSchedule": [
        { "type": "식사",   "name": "<지역 음식종류 맛집>"},
        { "type": "관광지", "name": "<공식 POI 명칭>"},
        { "type": "식사",   "name": "<지역 음식종류 맛집>"},
        { "type": "관광지", "name": "<공식 POI 명칭>"},
        { "type": "식사",   "name": "<지역 음식종류 맛집>"},
        { "type": "관광지", "name": "<공식 POI 명칭>"},
        { "type": "숙소",   "name": "<지역(동/읍/면) 업종>"}
      ]
    }
  ]
}

""");

        return sb.toString();
    }
}
