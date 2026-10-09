package com.localflow.domain.provider.controller;

import com.localflow.global.common.ApiResponse;
import com.localflow.domain.provider.dto.ProviderResponse;
import com.localflow.domain.provider.service.ProviderCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI Providers", description = "AI 제공자 목록과 지원 상태")
@RestController
@RequestMapping("/api/ai/providers")
public class ProviderController {
    private final ProviderCatalogService providerCatalogService;

    public ProviderController(ProviderCatalogService providerCatalogService) {
        this.providerCatalogService = providerCatalogService;
    }

    @Operation(summary = "AI 제공자 구현 및 환경 설정 상태 조회")
    @GetMapping
    public ApiResponse<List<ProviderResponse>> findAll(
            @RequestParam(defaultValue = "false") boolean refresh) {
        return ApiResponse.success(providerCatalogService.findAll(refresh));
    }
}
