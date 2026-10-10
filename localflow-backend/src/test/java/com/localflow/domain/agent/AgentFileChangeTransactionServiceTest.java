package com.localflow.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.localflow.domain.agent.domain.AgentPlan;
import com.localflow.domain.agent.domain.FileOperationAction;
import com.localflow.domain.agent.domain.FileOperationPlan;
import com.localflow.domain.agent.service.AgentFileApplyException;
import com.localflow.domain.agent.service.AgentFileChangeTransactionService;
import com.localflow.domain.workspace.domain.ProjectFileBackup;
import com.localflow.domain.workspace.dto.FileWriteRequest;
import com.localflow.domain.workspace.repository.ProjectFileRepository;
import com.localflow.domain.workspace.service.ProjectFileService;
import com.localflow.domain.workspace.service.WorkspaceStorageService;
import com.localflow.global.error.CustomException;
import com.localflow.global.error.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentFileChangeTransactionServiceTest {
    private final ProjectFileRepository fileRepository = mock(ProjectFileRepository.class);
    private final ProjectFileService fileService = mock(ProjectFileService.class);
    private final WorkspaceStorageService storageService = mock(WorkspaceStorageService.class);
    private AgentFileChangeTransactionService service;

    @BeforeEach
    void setUp() {
        service = new AgentFileChangeTransactionService(fileRepository, fileService, storageService);
        when(fileRepository.findAllByProject_IdOrderByRelativePath("project")).thenReturn(List.of());
    }

    @Test
    void reportsRecoveryRequiredPathsWhenRollbackAlsoFails() {
        when(storageService.normalizeRelativePath("new.txt")).thenReturn("new.txt");
        when(fileService.backup("project", "new.txt"))
                .thenReturn(ProjectFileBackup.missing("new.txt"));
        doThrow(new IllegalStateException("apply failed"))
                .when(fileService).write(eq("project"), eq(null), any(FileWriteRequest.class));
        doThrow(new IllegalStateException("rollback failed"))
                .when(fileService).restoreBackups(eq("project"), any());
        AgentPlan plan = new AgentPlan("create", "create", List.of(
                new FileOperationPlan(FileOperationAction.CREATE, "new.txt", null, "value")));

        assertThatThrownBy(() -> service.apply("project", plan, () -> { }))
                .isInstanceOfSatisfying(AgentFileApplyException.class, exception -> {
                    assertThat(exception.result().rollbackSuccessful()).isFalse();
                    assertThat(exception.result().recoveryRequiredPaths()).containsExactly("new.txt");
                });
    }

    @Test
    void rejectsPathOutsideProjectBeforeTakingBackupOrApplyingChanges() {
        when(storageService.normalizeRelativePath("../escape.txt"))
                .thenThrow(new CustomException(ErrorCode.INVALID_FILE_PATH));
        AgentPlan plan = new AgentPlan("escape", "escape", List.of(
                new FileOperationPlan(FileOperationAction.CREATE, "../escape.txt", null, "value")));

        assertThatThrownBy(() -> service.apply("project", plan, () -> { }))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.INVALID_FILE_PATH));
        verify(fileService, never()).backup(any(), any());
        verify(fileService, never()).write(any(), any(), any());
    }
}
