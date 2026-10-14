package com.localflow.domain.validation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localflow.domain.project.service.ProjectService;
import com.localflow.domain.validation.domain.ValidationBuildTool;
import com.localflow.domain.validation.domain.ValidationCommandSpec;
import com.localflow.domain.validation.domain.ValidationCommandType;
import com.localflow.domain.validation.dto.ProjectValidationPreviewResponse;
import com.localflow.domain.workspace.entity.ProjectFile;
import com.localflow.domain.workspace.repository.ProjectFileRepository;
import com.localflow.domain.workspace.service.WorkspaceStorageService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProjectValidationDiscoveryService {
    private static final List<NpmScript> NPM_SCRIPTS = List.of(
            new NpmScript("typecheck", ValidationCommandType.TYPECHECK, "TypeScript 타입 검사"),
            new NpmScript("lint", ValidationCommandType.LINT, "정적 분석"),
            new NpmScript("test", ValidationCommandType.TEST, "테스트"),
            new NpmScript("build", ValidationCommandType.BUILD, "프론트엔드 빌드")
    );

    private final ProjectService projectService;
    private final ProjectFileRepository fileRepository;
    private final WorkspaceStorageService storageService;
    private final ObjectMapper objectMapper;

    public ProjectValidationDiscoveryService(ProjectService projectService,
                                             ProjectFileRepository fileRepository,
                                             WorkspaceStorageService storageService,
                                             ObjectMapper objectMapper) {
        this.projectService = projectService;
        this.fileRepository = fileRepository;
        this.storageService = storageService;
        this.objectMapper = objectMapper;
    }

    public ProjectValidationPreviewResponse preview(String projectId) {
        List<ValidationCommandSpec> specs = discover(projectId);
        List<String> notices = new ArrayList<>();
        if (specs.isEmpty()) {
            notices.add("지원하는 Gradle 또는 npm 검증 구성을 찾지 못했습니다.");
        } else {
            notices.add("검증 명령은 업로드된 프로젝트 사본에서만 실행됩니다.");
            notices.add("실행은 사용자가 명령과 대상 디렉터리를 확인한 후 승인해야 합니다.");
        }
        return ProjectValidationPreviewResponse.from(projectId, specs, notices);
    }

    public List<ValidationCommandSpec> discover(String projectId) {
        projectService.requireProject(projectId);
        List<ProjectFile> files = fileRepository.findAllByProject_IdOrderByRelativePath(projectId);
        Set<String> paths = files.stream().map(ProjectFile::getRelativePath)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<ValidationCommandSpec> specs = new ArrayList<>();
        for (ProjectFile file : files) {
            String path = file.getRelativePath();
            String name = file.getFileName().toLowerCase(Locale.ROOT);
            if (name.equals("build.gradle") || name.equals("build.gradle.kts")) {
                addGradle(specs, paths, path);
            } else if (name.equals("package.json")) {
                addNpm(projectId, specs, path);
            }
        }
        return specs.stream().sorted(Comparator
                .comparing(ValidationCommandSpec::workingDirectory)
                .thenComparing(spec -> spec.tool().name())
                .thenComparing(spec -> spec.type().name())).toList();
    }

    private void addGradle(List<ValidationCommandSpec> specs, Set<String> paths, String buildFile) {
        String directory = parent(buildFile);
        String windowsWrapper = child(directory, "gradlew.bat");
        String unixWrapper = child(directory, "gradlew");
        String executable = paths.contains(windowsWrapper) ? "gradlew.bat"
                : paths.contains(unixWrapper) ? "gradlew" : "gradle";
        add(specs, ValidationBuildTool.GRADLE, ValidationCommandType.COMPILE, directory,
                buildFile, "Java 컴파일", executable, List.of("compileJava", "--no-daemon"));
        add(specs, ValidationBuildTool.GRADLE, ValidationCommandType.TEST, directory,
                buildFile, "Gradle 테스트", executable, List.of("test", "--no-daemon"));
    }

    private void addNpm(String projectId, List<ValidationCommandSpec> specs, String packageJson) {
        JsonNode scripts;
        try {
            scripts = objectMapper.readTree(storageService.readText(projectId, packageJson)).path("scripts");
        } catch (Exception ignored) {
            return;
        }
        if (!scripts.isObject()) return;
        String directory = parent(packageJson);
        for (NpmScript script : NPM_SCRIPTS) {
            if (scripts.path(script.name()).isTextual()) {
                add(specs, ValidationBuildTool.NPM, script.type(), directory, packageJson,
                        script.displayName(), "npm", List.of("run", script.name()));
            }
        }
    }

    private void add(List<ValidationCommandSpec> specs, ValidationBuildTool tool,
                     ValidationCommandType type, String directory, String buildFile,
                     String displayName, String executable, List<String> arguments) {
        String display = executable + " " + String.join(" ", arguments);
        specs.add(new ValidationCommandSpec(id(tool, type, directory), tool, type,
                directory, buildFile, displayName, display, executable, arguments));
    }

    private String id(ValidationBuildTool tool, ValidationCommandType type, String directory) {
        String value = tool + "|" + type + "|" + directory;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return tool.name().toLowerCase(Locale.ROOT) + "-"
                    + type.name().toLowerCase(Locale.ROOT) + "-"
                    + HexFormat.of().formatHex(digest, 0, 6);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String parent(String path) {
        int index = path.lastIndexOf('/');
        return index < 0 ? "." : path.substring(0, index);
    }

    private String child(String directory, String name) {
        return directory.equals(".") ? name : directory + "/" + name;
    }

    private record NpmScript(String name, ValidationCommandType type, String displayName) {
    }
}
