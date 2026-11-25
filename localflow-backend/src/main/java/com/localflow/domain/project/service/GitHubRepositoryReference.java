package com.localflow.domain.project.service;

import com.localflow.global.error.CustomException;
import com.localflow.global.error.ErrorCode;
import java.net.URI;
import java.util.regex.Pattern;

public record GitHubRepositoryReference(String owner, String repository, String branch) {
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9_.-]{1,100}");
    private static final Pattern BRANCH = Pattern.compile("[A-Za-z0-9._/-]{1,200}");

    public static GitHubRepositoryReference parse(String value, String requestedBranch) {
        try {
            URI uri = URI.create(value == null ? "" : value.strip());
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !"github.com".equalsIgnoreCase(uri.getHost())
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw invalid();
            }
            String[] segments = uri.getPath().replaceAll("^/+|/+$", "").split("/");
            if (segments.length != 2) throw invalid();
            String owner = segments[0];
            String repository = segments[1].replaceFirst("(?i)\\.git$", "");
            if (!SEGMENT.matcher(owner).matches() || !SEGMENT.matcher(repository).matches()) throw invalid();
            String branch = requestedBranch == null || requestedBranch.isBlank()
                    ? null : requestedBranch.strip();
            if (branch != null && (!BRANCH.matcher(branch).matches() || branch.startsWith("-")
                    || branch.startsWith("/") || branch.endsWith("/") || branch.contains(".."))) {
                throw invalid();
            }
            return new GitHubRepositoryReference(owner, repository, branch);
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    public String cloneUrl() {
        return "https://github.com/" + owner + "/" + repository + ".git";
    }

    private static CustomException invalid() {
        return new CustomException(ErrorCode.INVALID_GITHUB_REPOSITORY);
    }
}
