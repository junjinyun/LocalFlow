package com.localflow.domain.workspace;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.localflow.domain.project.dto.request.ProjectCreateRequest;
import com.localflow.domain.project.dto.response.ProjectResponse;
import com.localflow.domain.project.service.ProjectService;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectFileControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ProjectService projectService;

    @Test
    void synchronizesFilesWithSingleJsonManifestPart() throws Exception {
        ProjectResponse project = projectService.create(new ProjectCreateRequest("manifest-project", null));
        var paths = IntStream.range(0, 12)
                .mapToObj(index -> "manifest-project/src/File" + index + ".txt")
                .toList();
        String pathJson = paths.stream()
                .map(path -> "\"" + path + "\"")
                .collect(Collectors.joining(","));
        MockMultipartFile manifest = new MockMultipartFile("manifest", "manifest.json",
                "application/json", ("{\"relativePaths\":[" + pathJson + "]}")
                .getBytes(StandardCharsets.UTF_8));

        var builder = multipart("/api/projects/{projectId}/files/sync", project.id());
        builder.file(manifest).param("fullSync", "true");
        for (int index = 0; index < paths.size(); index++) {
            builder.file(new MockMultipartFile("files", "File" + index + ".txt",
                    "text/plain", ("content-" + index).getBytes(StandardCharsets.UTF_8)));
        }

        mockMvc.perform(builder.with(request -> {
                    request.setMethod("PUT");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.added").value(12))
                .andExpect(jsonPath("$.data.totalFiles").value(12));
    }
}
