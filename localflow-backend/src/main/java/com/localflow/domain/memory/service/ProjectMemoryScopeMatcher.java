package com.localflow.domain.memory.service;

import com.localflow.domain.memory.entity.ProjectMemory;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ProjectMemoryScopeMatcher {
    public boolean matches(ProjectMemory memory, String taskType,
                           Set<String> selectedTags, List<String> selectedFiles) {
        if (!memory.isActive()) return false;
        if (memory.isGlobalScope()) return true;
        boolean constrained = false;
        if (!memory.getScopeTaskTypes().isEmpty()) {
            constrained = true;
            if (taskType == null || !memory.getScopeTaskTypes().contains(taskType.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        if (!memory.getScopeTags().isEmpty()) {
            constrained = true;
            if (selectedTags.stream().map(value -> value.toLowerCase(Locale.ROOT))
                    .noneMatch(memory.getScopeTags()::contains)) return false;
        }
        if (!memory.getScopeFilePatterns().isEmpty()) {
            constrained = true;
            boolean fileMatches = selectedFiles.stream().anyMatch(file -> memory.getScopeFilePatterns().stream()
                    .anyMatch(pattern -> globMatches(pattern, file)));
            if (!fileMatches) return false;
        }
        return constrained;
    }

    boolean globMatches(String glob, String path) {
        String normalizedGlob = glob.replace('\\', '/').toLowerCase(Locale.ROOT);
        String normalizedPath = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        StringBuilder regex = new StringBuilder("^");
        for (int index = 0; index < normalizedGlob.length(); index++) {
            char value = normalizedGlob.charAt(index);
            if (value == '*') {
                if (index + 1 < normalizedGlob.length() && normalizedGlob.charAt(index + 1) == '*') {
                    regex.append(".*");
                    index++;
                } else {
                    regex.append("[^/]*");
                }
            } else if (value == '?') {
                regex.append("[^/]");
            } else {
                regex.append(Pattern.quote(String.valueOf(value)));
            }
        }
        return normalizedPath.matches(regex.append('$').toString());
    }
}
