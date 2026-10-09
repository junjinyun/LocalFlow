package com.localflow.domain.workspace.service;

import com.localflow.domain.workspace.domain.SymbolType;
import com.localflow.domain.workspace.entity.CodeSymbol;
import com.localflow.domain.workspace.entity.ProjectFile;
import com.localflow.domain.workspace.repository.CodeSymbolRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class SourceIndexService {
    private static final Pattern JAVA_TYPE = Pattern.compile("\\b(class|interface|enum|record)\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern JAVA_METHOD = Pattern.compile("(?:public|protected|private|static|final|synchronized|abstract|native|\\s)+[\\w<>\\[\\], ?]+\\s+([A-Za-z_$][\\w$]*)\\s*\\([^;]*\\)\\s*(?:throws [^{]+)?\\{");
    private static final Pattern PYTHON_SYMBOL = Pattern.compile("^\\s*(class|def|async\\s+def)\\s+([A-Za-z_][\\w]*)");
    private static final Pattern JS_FUNCTION = Pattern.compile("\\b(?:async\\s+)?function\\s+([A-Za-z_$][\\w$]*)|\\bclass\\s+([A-Za-z_$][\\w$]*)|\\b(?:const|let|var)\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*(?:async\\s*)?\\([^)]*\\)\\s*=>");

    private final CodeSymbolRepository symbolRepository;

    public SourceIndexService(CodeSymbolRepository symbolRepository) {
        this.symbolRepository = symbolRepository;
    }

    public int reindex(ProjectFile projectFile, Path path) {
        symbolRepository.deleteAllByProjectFile_Id(projectFile.getId());
        projectFile.replaceTags(extractTags(projectFile, path));
        if (projectFile.getLanguage() == null) {
            return 0;
        }
        List<CodeSymbol> symbols = extract(projectFile, path);
        symbolRepository.saveAll(symbols);
        return symbols.size();
    }

    private Set<String> extractTags(ProjectFile file, Path path) {
        Set<String> tags = new LinkedHashSet<>();
        String relativePath = file.getRelativePath().toLowerCase(Locale.ROOT);
        String fileName = file.getFileName().toLowerCase(Locale.ROOT);
        tags.add(file.getCategory().name().toLowerCase(Locale.ROOT).replace('_', '-'));
        if (file.getLanguage() != null) tags.add(file.getLanguage().toLowerCase(Locale.ROOT));

        addWhen(tags, relativePath.contains("/controller/") || fileName.contains("controller"), "controller", "api");
        addWhen(tags, relativePath.contains("/service/") || fileName.contains("service"), "service");
        addWhen(tags, relativePath.contains("/repository/") || fileName.contains("repository"), "repository", "persistence");
        addWhen(tags, relativePath.contains("/entity/") || fileName.contains("entity"), "entity", "persistence");
        addWhen(tags, relativePath.contains("/dto/") || fileName.contains("request") || fileName.contains("response"), "dto", "api");
        addWhen(tags, relativePath.contains("/config/") || fileName.contains("config"), "configuration");
        addWhen(tags, relativePath.contains("/security/") || fileName.contains("security"), "security", "authentication");
        addWhen(tags, containsAny(relativePath, "auth", "login", "signin"), "authentication", "login");
        addWhen(tags, containsAny(relativePath, "jwt", "token"), "authentication", "jwt", "token");
        addWhen(tags, containsAny(relativePath, "/user/", "user", "member", "account"), "user");
        addWhen(tags, containsAny(relativePath, "/test/", ".test.", "test."), "test");
        addWhen(tags, containsAny(relativePath, "frontend", "/src/components/", "/src/pages/"), "frontend");
        addWhen(tags, containsAny(relativePath, "backend", "/src/main/java/", "/src/main/kotlin/"), "backend");
        addWhen(tags, containsAny(fileName, "build.gradle", "pom.xml", "package.json"), "build-config", "dependency");
        addWhen(tags, containsAny(fileName, "application.yml", "application.yaml", "application.properties", ".env"), "configuration");

        String content = readablePrefix(path, 200_000).toLowerCase(Locale.ROOT);
        addWhen(tags, containsAny(content, "@restcontroller", "@controller", "router", "express()"), "controller", "api");
        addWhen(tags, content.contains("@service"), "service");
        addWhen(tags, containsAny(content, "@repository", "jparepository", "crudrepository"), "repository", "persistence");
        addWhen(tags, containsAny(content, "@entity", "@table("), "entity", "persistence");
        addWhen(tags, containsAny(content, "springframework.security", "securityfilterchain", "enablewebsecurity"), "security", "authentication", "spring-security");
        addWhen(tags, containsAny(content, "jsonwebtoken", "jwts.", "bearer ", "jwt"), "authentication", "jwt", "token");
        addWhen(tags, containsAny(content, "passwordencoder", "login", "signin"), "authentication", "login");
        addWhen(tags, containsAny(content, "axios", "fetch("), "api-client");
        return tags;
    }

    private String readablePrefix(Path path, int maxChars) {
        try {
            String value = Files.readString(path, StandardCharsets.UTF_8);
            return value.length() <= maxChars ? value : value.substring(0, maxChars);
        } catch (IOException exception) {
            return "";
        }
    }

    private void addWhen(Set<String> tags, boolean condition, String... values) {
        if (condition) tags.addAll(List.of(values));
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }

    private List<CodeSymbol> extract(ProjectFile file, Path path) {
        List<CodeSymbol> symbols = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                switch (file.getLanguage()) {
                    case "Java", "Kotlin", "C#" -> extractJavaLike(file, line, lineNumber, symbols);
                    case "Python" -> extractPython(file, line, lineNumber, symbols);
                    case "JavaScript", "TypeScript" -> extractJavascript(file, line, lineNumber, symbols);
                    default -> { }
                }
            }
            return symbols;
        } catch (IOException exception) {
            return List.of();
        }
    }

    private void extractJavaLike(ProjectFile file, String line, int lineNumber, List<CodeSymbol> symbols) {
        Matcher type = JAVA_TYPE.matcher(line);
        if (type.find()) {
            SymbolType symbolType = switch (type.group(1)) {
                case "interface" -> SymbolType.INTERFACE;
                case "enum" -> SymbolType.ENUM;
                case "record" -> SymbolType.RECORD;
                default -> SymbolType.CLASS;
            };
            symbols.add(new CodeSymbol(file, type.group(2), symbolType, lineNumber));
            return;
        }
        Matcher method = JAVA_METHOD.matcher(line);
        if (method.find() && !line.stripLeading().startsWith("//")) {
            symbols.add(new CodeSymbol(file, method.group(1), SymbolType.METHOD, lineNumber));
        }
    }

    private void extractPython(ProjectFile file, String line, int lineNumber, List<CodeSymbol> symbols) {
        Matcher matcher = PYTHON_SYMBOL.matcher(line);
        if (matcher.find()) {
            SymbolType type = matcher.group(1).equals("class") ? SymbolType.CLASS : SymbolType.FUNCTION;
            symbols.add(new CodeSymbol(file, matcher.group(2), type, lineNumber));
        }
    }

    private void extractJavascript(ProjectFile file, String line, int lineNumber, List<CodeSymbol> symbols) {
        Matcher matcher = JS_FUNCTION.matcher(line);
        if (matcher.find()) {
            String name = matcher.group(1) != null ? matcher.group(1)
                    : matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            SymbolType type = matcher.group(2) != null ? SymbolType.CLASS : SymbolType.FUNCTION;
            symbols.add(new CodeSymbol(file, name, type, lineNumber));
        }
    }
}
