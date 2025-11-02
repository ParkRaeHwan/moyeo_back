package com.example.capstone.util.gpt;

import com.example.capstone.plan.dto.request.ScheduleCreateReqDto;
import com.example.capstone.plan.entity.City;
import com.example.capstone.user.entity.MBTI;
import com.example.capstone.plan.entity.PeopleGroup;
import com.example.capstone.matching.entity.TravelStyle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
public class GptRecreatePromptBuilder {

    public String build(ScheduleCreateReqDto request, List<String> excludedNames) {
        StringBuilder sb = new StringBuilder();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일");
        String start = request.getStartDate().format(fmt);
        String end   = request.getEndDate().format(fmt);

        boolean isDomestic = request.getDestination() == City.NONE;
        String destinationText = isDomestic ? "국내" : request.getDestination().getDisplayName();

        sb.append(String.format("""
다음 요구사항에 따라 **기존 일정과 겹치지 않도록** 여행 일정을 다시 생성하라.

[여행 정보]
- 기간: %s ~ %s
- 목적지: %s
""", start, end, destinationText));

        if (isDomestic) {
            sb.append("""
- 목적지 '국내'인 경우: 계절/MBTI/여행성향/예산/인원을 고려하여 **대한민국의 실제 '시(市) 단위' 도시 1곳만** 스스로 선택하고, **전 일정 전체**를 그 **단일 도시 경계 내부**에서만 구성한다.
""");
        }

        if (request.getMbti() != MBTI.NONE)              sb.append("- MBTI: ").append(request.getMbti()).append("\n");
        if (request.getTravelStyle() != TravelStyle.NONE) sb.append("- 여행 성향: ").append(request.getTravelStyle()).append("\n");
        if (request.getPeopleGroup() != PeopleGroup.NONE) sb.append("- 여행 인원: ").append(request.getPeopleGroup()).append("명\n");
        if (request.getBudget() != null)                  sb.append("- 예산: ").append(request.getBudget()).append("원\n");

        sb.append("\n[입력 제외 목록(JSON)]\n{\n  \"excludedNames\": [\n");
        if (excludedNames != null && !excludedNames.isEmpty()) {
            for (int i = 0; i < excludedNames.size(); i++) {
                String name = excludedNames.get(i);
                if (name == null) continue;
                name = name.replace("\"", "\\\"");
                sb.append("    \"").append(name).append("\"");
                if (i < excludedNames.size() - 1) sb.append(",");
                sb.append("\n");
            }
        }
        sb.append("  ]\n}\n");

        sb.append("""
        
[중복 규칙]
- (외부 중복) 위 JSON의 excludedNames와 **정규화 비교** 후 하나라도 일치 또는 부분일치(토큰 70%+) 시 **무효**이며, 해당 항목은 즉시 다른 후보로 **교체/재생성**한다.
- (내부 중복)
  · **식사/숙소**: 전 일정에서 **중복 0건**(정규화 비교 적용).
  · **관광지**: 기본적으로 중복 금지이나, **실존 관광지가 부족한 경우에 한해 '대표 관광지'의 제한적 중복을 허용**한다.
    - 허용 조건:
      1) 같은 관광지명 재사용은 **여행 전체에서 최대 1회(총 2회까지 등장)**.
      2) **동일 날짜 내 중복 금지**(하루에 같은 관광지 2번 금지).
      3) **연속 슬롯 배치 금지**(바로 앞/뒤 슬롯에 같은 관광지 배치 금지).
      4) 반드시 **공식 명칭 그대로** 사용하고, **동일 좌표(lat/lng)**를 유지한다.

""");

sb.append("""
        
[핵심 규칙]
1) 날짜별로 정확히 7개 항목만 생성하며, 순서를 엄격히 지킨다:
   [아침(식사) → 관광지 → 점심(식사) → 관광지 → 저녁(식사) → 관광지 → 숙소]
   - 누락/추가/순서변경 금지.
2) 날짜는 요청의 시작일~종료일을 모두 포함하여 "date"에 YYYY-MM-DD 로 표기한다.
3) 선택한 도시는 **하루라도 벗어나면 안 됨**(인접 시·군·구 이동 금지).
""");

sb.append("""
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
""");

sb.append("""
[숙소 규칙]
     - 형식: "지역(시/군/구) + 업종" 또는 "지역(시/군/구) + (실제 동/읍/면명) + 업종"
       - 예: "제주시 호텔", "서귀포시 리조트", "부산 해운대구 호텔", "제주시 성산읍 호텔"
     - 허용 업종: 호텔, 리조트, 콘도, 펜션, 게스트하우스, 한옥스테이, 모텔
     - 금지: 특정 브랜드/체인명(라마다, 롯데, 신라스테이 등)과 수식/감성어(힐링, 오션뷰, 프리미엄 등)
[숙소 예산 기준 (1인 1박 기준)]
    - 30만원 이하 구간:
      - 가격: 30,000~40,000원 수준의 저가 숙소
      - **허용 업종: 게스트하우스, 모텔만 사용 가능**
      - **금지 업종: 호텔, 리조트, 콘도, 펜션, 한옥스테이**
      - 고급스러운 이미지나 자연경관을 강조한 펜션·리조트는 선택하지 말 것.
      - 40~60만원 구간: 50,000~70,000원 수준의 일반 숙소 (펜션·한옥스테이·콘도)
     - 60~80만원 구간: 70,000~90,000원 수준의 중상급 숙소 (호텔·리조트)
     - 80~100만원 구간: 90,000~120,000원 수준의 고급 숙소 (리조트·호텔)

     [식사 규칙]
     - 형식(정확히): "지역 + 음식종류 + 맛집"
       - 예: "제주시 전복죽 맛집", "대구시 막창 맛집"
     - 음식종류는 **실제 음식점 업종에서 흔히 쓰이는 '단일 음식명'**만 허용.
     - **허용 예시**: 곰탕, 삼겹살, 막창, 회, 초밥, 갈비, 닭갈비, 막국수, 칼국수, 순대, 해장국, 전복죽, 냉면, 갈치조림, 아구찜, 보쌈, 족발, 콩나물국밥, 부대찌개, 찌개, 전골, 우동, 돈까스, 파스타, 피자, 옹심이
     - **금지**:
       - 단일 식재료/농산물/가공식품(옥수수, 감자, 고구마, 토마토, 쌀, 밀, 콩 등)
       - 음료(커피, 차, 주스 등), 디저트(빙수, 케이크, 아이스크림 등), 감성어(현지인 추천, 전통, 브런치 등)
       - 시간대(아침국수, 야식 등), 조합/복합수식(매실갈비, 허브족발, 건강식, 퓨전요리 등)
     [식사 예산 기준 (1인 1식 기준)]
       - 30만원 이하 구간: 8,000~12,000원 (가성비 식당 위주)
       - 40~60만원 구간: 10,000~15,000원 (일반 맛집)
       - 60~80만원 구간: 15,000~20,000원 (중간~상급 식당)
       - 80~100만원 구간: 20,000~25,000원 (지역 대표 음식, 고급 맛집)
""");

sb.append("""
[자체 검증 & 재생성 루프(필수)]
최종 출력 전 다음을 순서대로 점검하고, 하나라도 실패하면 **전체 결과를 내부에서 폐기하고 새로 생성**하여
모든 항목 통과본만 출력한다. (검증/재생성 과정은 출력하지 않는다)
1) (E1) excludedNames와의 외부 중복 0건(정규화 적용)
2) (E2a) 식사/숙소 내부 중복 0건(정규화 적용)
3) (E2b) 관광지 내부 중복은 '대표 관광지 제한적 중복 허용' 규칙 충족(최대 1회 재사용, 동일 날짜/연속 슬롯 금지, 좌표 동일)
4) (S) 날짜별 7개 및 순서 일치
5) (C) 단일 도시 경계 위반 없음
6) (P) 관광지의 화이트/블랙 규정 위반 없음
7) (F) 식사/숙소 포맷 위반 없음(실매장/브랜드/지점명 금지)
""");

sb.append("""
[출력 스키마]
{
  "itinerary": [
    {
      "date": "YYYY-MM-DD",
      "travelSchedule": [
        { "type": "식사",   "name": "<지역 음식종류 맛집>" },
        { "type": "관광지", "name": "<공식 POI 명칭>" },
        { "type": "식사",   "name": "<지역 음식종류 맛집>" },
        { "type": "관광지", "name": "<공식 POI 명칭>" },
        { "type": "식사",   "name": "<지역 음식종류 맛집>" },
        { "type": "관광지", "name": "<공식 POI 명칭>" },
        { "type": "숙소",   "name": "<지역(동/읍/면) 업종>" }
      ]
    }
  ]
}

""");

        return sb.toString();
    }
}
