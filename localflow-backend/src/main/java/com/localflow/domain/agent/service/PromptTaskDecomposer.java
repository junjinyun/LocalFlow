package com.localflow.domain.agent.service;

import com.localflow.domain.provider.domain.TaskScope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class PromptTaskDecomposer {
    private static final int MAX_TASKS = 6;
    private static final Pattern SEPARATOR = Pattern.compile(
            "(?i)\\s*(?:그리고|그\\s*다음|그\\s*후|이후|후에|한\\s*뒤|다음으로|및)\\s*|\\s+후\\s+|[;\\n]+");
    private static final Map<String, List<String>> TAG_KEYWORDS = keywords();

    public List<String> decompose(String prompt) {
        if (prompt == null || prompt.isBlank()) return List.of();
        List<String> tasks = new ArrayList<>();
        for (String value : SEPARATOR.split(prompt.strip())) {
            String task = value.strip().replaceAll("^[,.:\\-]+|[,.:\\-]+$", "").strip();
            if (task.length() >= 2 && !tasks.contains(task)) tasks.add(task);
            if (tasks.size() == MAX_TASKS) break;
        }
        return tasks.size() > 1 ? List.copyOf(tasks) : List.of(prompt.strip());
    }

    public List<TaskScope> scopes(String prompt, List<String> availableTags) {
        return decompose(prompt).stream()
                .map(task -> new TaskScope(task, tagsFor(task, availableTags)))
                .toList();
    }

    public List<String> tagsFor(String task, List<String> availableTags) {
        String lower = task.toLowerCase(Locale.ROOT);
        Set<String> selected = new LinkedHashSet<>();
        TAG_KEYWORDS.forEach((tag, terms) -> {
            if (terms.stream().anyMatch(lower::contains)) selected.add(tag);
        });
        if (availableTags != null) availableTags.stream()
                .filter(tag -> lower.contains(tag.toLowerCase(Locale.ROOT)))
                .forEach(selected::add);
        return List.copyOf(selected);
    }

    private static Map<String, List<String>> keywords() {
        Map<String, List<String>> values = new LinkedHashMap<>();
        values.put("authentication", List.of("인증", "로그인", "auth", "login", "signin", "jwt", "토큰"));
        values.put("login", List.of("로그인", "login", "signin", "password"));
        values.put("jwt", List.of("jwt", "토큰", "token", "bearer"));
        values.put("security", List.of("보안", "security", "권한", "인가"));
        values.put("user", List.of("사용자", "회원", "유저", "user", "member", "account"));
        values.put("controller", List.of("api", "엔드포인트", "요청", "controller"));
        values.put("service", List.of("서비스", "비즈니스 로직", "service"));
        values.put("repository", List.of("저장", "조회", "데이터베이스", "repository"));
        values.put("entity", List.of("엔티티", "테이블", "필드", "entity"));
        values.put("configuration", List.of("설정", "config", "환경 변수"));
        values.put("dependency", List.of("의존성", "라이브러리", "dependency", "gradle", "maven", "npm"));
        values.put("test", List.of("테스트", "검증", "test"));
        values.put("frontend", List.of("프론트", "화면", "ui", "react"));
        values.put("backend", List.of("백엔드", "서버", "spring"));
        return Map.copyOf(values);
    }
}
