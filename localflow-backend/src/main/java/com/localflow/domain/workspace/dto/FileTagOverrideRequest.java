package com.localflow.domain.workspace.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record FileTagOverrideRequest(
        @Size(max = 20, message = "사용자 추가 태그는 최대 20개입니다.")
        Set<@Valid @NotBlank @Size(max = 40)
                @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*", message = "태그 형식이 올바르지 않습니다.") String> addedTags,
        @Size(max = 20, message = "사용자 제외 태그는 최대 20개입니다.")
        Set<@Valid @NotBlank @Size(max = 40)
                @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*", message = "태그 형식이 올바르지 않습니다.") String> excludedTags
) {
    public FileTagOverrideRequest {
        addedTags = addedTags == null ? Set.of() : Set.copyOf(addedTags);
        excludedTags = excludedTags == null ? Set.of() : Set.copyOf(excludedTags);
    }
}
