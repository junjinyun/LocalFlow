package com.localflow.domain.agent.service;

import com.localflow.domain.agent.domain.AgentPlan;
import com.localflow.domain.agent.domain.AgentResponseContract;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AgentResponsePolicy {
    private static final Pattern EVIDENCE_HEADING = Pattern.compile(
            "(?im)^\\s*(?:주요\\s*)?근거\\s*:\\s*$");
    private static final Pattern REQUIRED_QUOTED_PHRASE = Pattern.compile(
            "(?is)(?:마지막|종료|끝).{0,120}?[\\\"“]([^\\\"”\\r\\n]{2,200})[\\\"”]"
                    + ".{0,50}?(?:포함|추가|표시|끝)");
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile(
            "(?<=[.!?。！？])\\s+");

    private AgentResponsePolicy() {
    }

    public static AgentPlan apply(String originalRequest, String projectContext, AgentPlan plan) {
        AgentResponseContract contract = AgentResponseContract.from(originalRequest);
        ResponseParts parts = split(plan.response());
        List<String> extractedRequired = requiredPhrases(projectContext);
        List<String> body = sentences(parts.body()).stream()
                .filter(sentence -> extractedRequired.stream().noneMatch(sentence::contains))
                .toList();
        List<String> required = extractedRequired;
        List<String> findings = new ArrayList<>(parts.findings());

        if (contract.maxItems() != null && findings.size() > contract.maxItems()) {
            findings = new ArrayList<>(findings.subList(0, contract.maxItems()));
        }
        if (contract.maxSentences() != null) {
            int limit = contract.maxSentences();
            List<String> keptRequired = required.stream().limit(limit).toList();
            int remaining = Math.max(0, limit - keptRequired.size());
            int findingReserve = !findings.isEmpty() && remaining > 0 ? 1 : 0;
            int bodyLimit = Math.max(0, remaining - findingReserve);
            body = body.stream().limit(bodyLimit).toList();
            remaining -= body.size();
            findings = findings.stream().map(AgentResponsePolicy::firstSentence)
                    .limit(remaining).toList();
            required = keptRequired;
        }

        List<String> finalBody = new ArrayList<>(body);
        finalBody.addAll(required);
        if (finalBody.isEmpty() && !parts.body().isBlank()) finalBody.add(parts.body().strip());
        String response = String.join(" ", finalBody).strip();
        if (!findings.isEmpty()) {
            response += "\n\n주요 근거:\n- " + String.join("\n- ", findings);
        }
        return new AgentPlan(plan.summary(), response, plan.operations());
    }

    private static ResponseParts split(String response) {
        String value = response == null ? "" : response.strip();
        Matcher heading = EVIDENCE_HEADING.matcher(value);
        if (!heading.find()) return new ResponseParts(value, List.of());
        String body = value.substring(0, heading.start()).strip();
        List<String> findings = value.substring(heading.end()).lines()
                .map(String::strip)
                .filter(line -> line.startsWith("- "))
                .map(line -> line.substring(2).strip())
                .filter(line -> !line.isBlank())
                .distinct().toList();
        return new ResponseParts(body, findings);
    }

    private static List<String> requiredPhrases(String context) {
        if (context == null || context.isBlank()) return List.of();
        Set<String> phrases = new LinkedHashSet<>();
        Matcher matcher = REQUIRED_QUOTED_PHRASE.matcher(context);
        while (matcher.find()) phrases.add(matcher.group(1).strip());
        return List.copyOf(phrases);
    }

    private static List<String> sentences(String value) {
        if (value == null || value.isBlank()) return List.of();
        return List.of(SENTENCE_BOUNDARY.split(value.strip())).stream()
                .map(String::strip).filter(sentence -> !sentence.isBlank()).toList();
    }

    private static String firstSentence(String value) {
        List<String> values = sentences(value);
        return values.isEmpty() ? value.strip() : values.get(0);
    }

    private record ResponseParts(String body, List<String> findings) {
    }
}
