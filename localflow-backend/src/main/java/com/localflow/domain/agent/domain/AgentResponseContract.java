package com.localflow.domain.agent.domain;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record AgentResponseContract(Integer maxSentences, Integer maxItems) {
    private static final String NUMBER = "(\\d{1,2}|한|두|세|네|다섯|여섯|일곱|여덟|아홉|열)";
    private static final Pattern SENTENCES = Pattern.compile(
            "(?:최대\\s*)?" + NUMBER + "\\s*문장(?:\\s*(?:이내|이하|으로))?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ITEMS = Pattern.compile(
            "최대\\s*" + NUMBER + "\\s*(?:개|건|항목)|"
                    + NUMBER + "\\s*(?:개|건|항목)\\s*(?:이내|이하)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ENGLISH_SENTENCES = Pattern.compile(
            "(?:at\\s+most|within)\\s+(\\d{1,2})\\s+sentences?",
            Pattern.CASE_INSENSITIVE);
    private static final Map<String, Integer> KOREAN_NUMBERS = Map.of(
            "한", 1, "두", 2, "세", 3, "네", 4, "다섯", 5,
            "여섯", 6, "일곱", 7, "여덟", 8, "아홉", 9, "열", 10);

    public static AgentResponseContract from(String request) {
        String value = request == null ? "" : request.strip();
        Integer sentences = firstNumber(SENTENCES.matcher(value));
        if (sentences == null) sentences = firstNumber(ENGLISH_SENTENCES.matcher(value));
        Integer items = firstNumber(ITEMS.matcher(value));
        return new AgentResponseContract(valid(sentences), valid(items));
    }

    public boolean constrained() {
        return maxSentences != null || maxItems != null;
    }

    public String instruction() {
        if (!constrained()) return "- 원본 요청에 명시된 분량과 출력 형식을 그대로 지킨다.";
        StringBuilder result = new StringBuilder();
        if (maxSentences != null) {
            result.append("- 최종 표시 결과 전체를 최대 ").append(maxSentences)
                    .append("문장으로 제한한다. response 문장과 findings 각 항목, 필수 종료 문구를 모두 문장 수에 포함한다.\n");
        }
        if (maxItems != null) {
            result.append("- 목록과 findings를 합쳐 최대 ").append(maxItems)
                    .append("개 항목만 반환한다.\n");
        }
        result.append("- 분량 제한 때문에 근거를 줄여야 하면 가장 직접적인 파일 근거부터 남긴다.");
        return result.toString();
    }

    private static Integer firstNumber(Matcher matcher) {
        if (!matcher.find()) return null;
        for (int index = 1; index <= matcher.groupCount(); index++) {
            String group = matcher.group(index);
            if (group == null) continue;
            Integer korean = KOREAN_NUMBERS.get(group.toLowerCase(Locale.ROOT));
            if (korean != null) return korean;
            if (group.chars().allMatch(Character::isDigit)) return Integer.parseInt(group);
        }
        return null;
    }

    private static Integer valid(Integer value) {
        return value == null || value < 1 || value > 50 ? null : value;
    }
}
