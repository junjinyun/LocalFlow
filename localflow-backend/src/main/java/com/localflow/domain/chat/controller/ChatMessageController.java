package com.localflow.domain.chat.controller;

import com.localflow.domain.chat.dto.ChatMessageCreateRequest;
import com.localflow.domain.chat.dto.ChatMessageResponse;
import com.localflow.domain.chat.service.ChatMessageService;
import com.localflow.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Project Chat", description = "프로젝트 프롬프트 및 인수인계 기록")
@RestController
@RequestMapping("/api/projects/{projectId}/messages")
public class ChatMessageController {
    private final ChatMessageService service;

    public ChatMessageController(ChatMessageService service) {
        this.service = service;
    }

    @Operation(summary = "채팅 기록 추가")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChatMessageResponse> create(@PathVariable String projectId,
                                                   @Valid @RequestBody ChatMessageCreateRequest request) {
        return ApiResponse.success(service.create(projectId, request));
    }

    @Operation(summary = "채팅 기록 조회")
    @GetMapping
    public ApiResponse<List<ChatMessageResponse>> findAll(@PathVariable String projectId) {
        return ApiResponse.success(service.findAll(projectId));
    }

    @Operation(summary = "채팅 기록 삭제")
    @DeleteMapping("/{messageId}")
    public ApiResponse<Void> delete(@PathVariable String projectId, @PathVariable String messageId) {
        service.delete(projectId, messageId);
        return ApiResponse.success(null);
    }
}
