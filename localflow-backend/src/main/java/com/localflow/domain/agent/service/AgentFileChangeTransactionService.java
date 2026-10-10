package com.localflow.domain.agent.service;

import com.localflow.domain.agent.domain.AgentApplyResult;
import com.localflow.domain.agent.domain.AgentPlan;
import com.localflow.domain.agent.domain.FileOperationPlan;
import com.localflow.domain.workspace.domain.ProjectFileBackup;
import com.localflow.domain.workspace.dto.FileMoveRequest;
import com.localflow.domain.workspace.dto.FileWriteRequest;
import com.localflow.domain.workspace.entity.ProjectFile;
import com.localflow.domain.workspace.repository.ProjectFileRepository;
import com.localflow.domain.workspace.service.ProjectFileService;
import com.localflow.domain.workspace.service.WorkspaceStorageService;
import com.localflow.global.error.CustomException;
import com.localflow.global.error.ErrorCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class AgentFileChangeTransactionService {
    private final ProjectFileRepository fileRepository;
    private final ProjectFileService fileService;
    private final WorkspaceStorageService storageService;

    public AgentFileChangeTransactionService(ProjectFileRepository fileRepository,
                                             ProjectFileService fileService,
                                             WorkspaceStorageService storageService) {
        this.fileRepository = fileRepository;
        this.fileService = fileService;
        this.storageService = storageService;
    }

    public AgentApplyResult apply(String projectId, AgentPlan plan, Runnable cancellationCheckpoint) {
        List<FileOperationPlan> operations = normalizeAndValidate(projectId, plan.operations());
        List<ProjectFileBackup> backups = captureBackups(projectId, operations);
        int applied = 0;
        try {
            for (FileOperationPlan operation : operations) {
                cancellationCheckpoint.run();
                applyOne(projectId, operation);
                applied++;
            }
            cancellationCheckpoint.run();
            return new AgentApplyResult(true, applied, false, true, List.of(), null);
        } catch (RuntimeException exception) {
            try {
                fileService.restoreBackups(projectId, backups);
                AgentApplyResult result = new AgentApplyResult(false, applied, true,
                        true, List.of(), safeMessage(exception));
                throw new AgentFileApplyException("파일 변경 중 오류가 발생하여 원본 상태로 복구했습니다.",
                        exception, result);
            } catch (AgentFileApplyException wrapped) {
                throw wrapped;
            } catch (RuntimeException rollbackException) {
                List<String> recoveryPaths = backups.stream()
                        .map(ProjectFileBackup::relativePath).toList();
                AgentApplyResult result = new AgentApplyResult(false, applied, true,
                        false, recoveryPaths, safeMessage(exception));
                rollbackException.addSuppressed(exception);
                throw new AgentFileApplyException(
                        "파일 변경과 자동 복구에 실패했습니다. 수동 복구가 필요합니다.",
                        rollbackException, result);
            }
        }
    }

    private List<FileOperationPlan> normalizeAndValidate(String projectId,
                                                         List<FileOperationPlan> operations) {
        Set<String> existing = new LinkedHashSet<>();
        fileRepository.findAllByProject_IdOrderByRelativePath(projectId)
                .stream().map(ProjectFile::getRelativePath).forEach(existing::add);
        List<FileOperationPlan> normalized = new ArrayList<>();
        for (FileOperationPlan operation : operations) {
            String path = storageService.normalizeRelativePath(operation.path());
            String destination = operation.destinationPath() == null ? null
                    : storageService.normalizeRelativePath(operation.destinationPath());
            switch (operation.action()) {
                case CREATE -> {
                    if (existing.contains(path)) throw invalidPlan();
                    existing.add(path);
                }
                case UPDATE -> {
                    if (!existing.contains(path)) throw missingFile();
                }
                case DELETE -> {
                    if (!existing.remove(path)) throw missingFile();
                }
                case MOVE -> {
                    if (!existing.remove(path)) throw missingFile();
                    if (destination == null || !existing.add(destination)) throw invalidPlan();
                }
            }
            normalized.add(new FileOperationPlan(operation.action(), path, destination,
                    operation.content()));
        }
        return List.copyOf(normalized);
    }

    private List<ProjectFileBackup> captureBackups(String projectId,
                                                   List<FileOperationPlan> operations) {
        Map<String, ProjectFileBackup> backups = new LinkedHashMap<>();
        for (FileOperationPlan operation : operations) {
            backups.computeIfAbsent(operation.path(), path -> fileService.backup(projectId, path));
            if (operation.destinationPath() != null) {
                backups.computeIfAbsent(operation.destinationPath(),
                        path -> fileService.backup(projectId, path));
            }
        }
        return List.copyOf(backups.values());
    }

    private void applyOne(String projectId, FileOperationPlan operation) {
        ProjectFile current = fileRepository
                .findByProject_IdAndRelativePath(projectId, operation.path()).orElse(null);
        switch (operation.action()) {
            case CREATE -> fileService.write(projectId, null,
                    new FileWriteRequest(operation.path(), operation.content(), true));
            case UPDATE -> fileService.write(projectId, requireCurrent(current).getId(),
                    new FileWriteRequest(operation.path(), operation.content(), true));
            case MOVE -> fileService.move(projectId, requireCurrent(current).getId(),
                    new FileMoveRequest(operation.destinationPath(), true));
            case DELETE -> fileService.delete(projectId, requireCurrent(current).getId(), true);
        }
    }

    private ProjectFile requireCurrent(ProjectFile current) {
        if (current == null) throw missingFile();
        return current;
    }

    private CustomException missingFile() {
        return new CustomException(ErrorCode.PROJECT_FILE_NOT_FOUND);
    }

    private CustomException invalidPlan() {
        return new CustomException(ErrorCode.INVALID_AI_PLAN);
    }

    private String safeMessage(RuntimeException exception) {
        if (exception instanceof CustomException custom) return custom.errorCode().message();
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "파일 변경 중 오류가 발생했습니다." : message;
    }
}
