package com.localflow.domain.workspace.controller;

import com.localflow.domain.workspace.dto.FileContentResponse;
import com.localflow.domain.workspace.dto.FileMoveRequest;
import com.localflow.domain.workspace.dto.FileSyncManifest;
import com.localflow.domain.workspace.dto.FileSyncResponse;
import com.localflow.domain.workspace.dto.FileWriteRequest;
import com.localflow.domain.workspace.dto.ProjectFileResponse;
import com.localflow.domain.workspace.entity.ProjectFile;
import com.localflow.domain.workspace.service.ProjectFileService;
import com.localflow.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Tag(name = "Project Files", description = "프로젝트 디렉터리 업로드, 동기화 및 파일 작업")
@RestController
@RequestMapping("/api/projects/{projectId}")
public class ProjectFileController {
    private final ProjectFileService fileService;

    public ProjectFileController(ProjectFileService fileService) {
        this.fileService = fileService;
    }

    @Operation(summary = "업로드 디렉터리 동기화",
            description = "files와 동일한 순서의 경로를 manifest.relativePaths에 전달합니다. fullSync=true이면 누락 파일을 삭제로 처리합니다.")
    @PutMapping(value = "/files/sync", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileSyncResponse> synchronize(
            @PathVariable String projectId,
            @RequestPart("files") List<MultipartFile> files,
            @Valid @RequestPart("manifest") FileSyncManifest manifest,
            @RequestParam(defaultValue = "true") boolean fullSync,
            @RequestParam(required = false) String scope
    ) {
        return ApiResponse.success(fileService.synchronize(
                        projectId, files, manifest.relativePaths(), fullSync, scope),
                "프로젝트 파일 동기화가 완료되었습니다.");
    }

    @Operation(summary = "프로젝트 파일 목록 조회")
    @GetMapping("/files")
    public ApiResponse<List<ProjectFileResponse>> findAll(@PathVariable String projectId) {
        return ApiResponse.success(fileService.findAll(projectId));
    }

    @Operation(summary = "텍스트 기반 파일 내용과 코드 심볼 조회")
    @GetMapping("/files/{fileId}/content")
    public ApiResponse<FileContentResponse> readContent(@PathVariable String projectId,
                                                        @PathVariable String fileId) {
        return ApiResponse.success(fileService.readContent(projectId, fileId));
    }

    @Operation(summary = "파일 다운로드")
    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<Resource> download(@PathVariable String projectId,
                                             @PathVariable String fileId) {
        ProjectFile file = fileService.requireFile(projectId, fileId);
        Resource resource = fileService.resource(projectId, fileId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.getFileName()).build().toString())
                .body(resource);
    }

    @Operation(summary = "텍스트 파일 생성")
    @PostMapping("/files")
    public ApiResponse<ProjectFileResponse> create(@PathVariable String projectId,
                                                   @Valid @RequestBody FileWriteRequest request) {
        return ApiResponse.success(fileService.write(projectId, null, request), "파일이 생성되었습니다.");
    }

    @Operation(summary = "텍스트 파일 수정")
    @PutMapping("/files/{fileId}")
    public ApiResponse<ProjectFileResponse> update(@PathVariable String projectId,
                                                   @PathVariable String fileId,
                                                   @Valid @RequestBody FileWriteRequest request) {
        return ApiResponse.success(fileService.write(projectId, fileId, request), "파일이 수정되었습니다.");
    }

    @Operation(summary = "파일 이동 또는 이름 변경")
    @PostMapping("/files/{fileId}/move")
    public ApiResponse<ProjectFileResponse> move(@PathVariable String projectId,
                                                 @PathVariable String fileId,
                                                 @Valid @RequestBody FileMoveRequest request) {
        return ApiResponse.success(fileService.move(projectId, fileId, request), "파일이 이동되었습니다.");
    }

    @Operation(summary = "파일 삭제")
    @DeleteMapping("/files/{fileId}")
    public ApiResponse<Void> delete(@PathVariable String projectId,
                                    @PathVariable String fileId,
                                    @RequestParam(defaultValue = "false") boolean approved) {
        fileService.delete(projectId, fileId, approved);
        return ApiResponse.success(null, "파일이 삭제되었습니다.");
    }

    @Operation(summary = "작업 완료 디렉터리 ZIP 다운로드")
    @GetMapping(value = "/archive", produces = "application/zip")
    public ResponseEntity<StreamingResponseBody> archive(@PathVariable String projectId) {
        Path projectRoot = fileService.projectRoot(projectId);
        StreamingResponseBody body = output -> {
            try (ZipOutputStream zip = new ZipOutputStream(output)) {
                if (!Files.exists(projectRoot)) {
                    return;
                }
                try (var paths = Files.walk(projectRoot)) {
                    for (Path path : paths.filter(Files::isRegularFile).toList()) {
                        String entryName = projectRoot.relativize(path).toString().replace('\\', '/');
                        zip.putNextEntry(new ZipEntry(entryName));
                        Files.copy(path, zip);
                        zip.closeEntry();
                    }
                }
            } catch (IOException exception) {
                throw new IllegalStateException("ZIP 파일 생성에 실패했습니다.", exception);
            }
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(projectId + ".zip").build().toString())
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(body);
    }
}
