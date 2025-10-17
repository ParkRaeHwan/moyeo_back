package com.example.capstone.util.gpt;

import org.springframework.stereotype.Component;

@Component
public class GptDescriptionPromptBuilder {

    public String build(String placeName, String type) {
        return """
너는 여행 장소 설명을 작성하는 여행 가이드야.
다음 장소에 대해 한 줄로 요약된 설명을 제공해줘:

- 장소명: %s
- 유형: %s

[작성 규칙]
- 한 줄로 요약된 설명만 출력해.
- 마침표 없이 끝내.
- 설명은 30자 이내로 작성해.
- 관광지, 식사, 숙소 등 어떤 유형이든 여행객이 흥미를 느낄 수 있도록 설명해.
- 반드시 JSON 형식으로 아래처럼 출력해:

{
  \"description\": \"설명내용\"
}
""".formatted(placeName, type);
    }
}
