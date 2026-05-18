package com.example.chatagent.service.github;

import com.example.chatagent.config.GitHubProperties;
import org.springframework.stereotype.Service;

/**
 * Decides whether a given file path inside a synced GitHub repository
 * should be indexed, and classifies it as either {@code document} or
 * {@code code} so downstream chunking can use the right strategy.
 */
@Service
public class GitHubFileFilter {

    public static final String TYPE_DOCUMENT = "document";
    public static final String TYPE_CODE = "code";
    public static final String TYPE_OTHER = "other";

    private final GitHubProperties props;

    public GitHubFileFilter(GitHubProperties props) {
        this.props = props;
    }

    public boolean shouldInclude(String filePath) {
        if (filePath == null || filePath.isBlank()) return false;
        String normalised = filePath.replace('\\', '/');
        for (String excludeDir : props.getSync().getExcludeDirectories()) {
            if (excludeDir == null || excludeDir.isBlank()) continue;
            if (normalised.startsWith(excludeDir + "/")
                    || normalised.contains("/" + excludeDir + "/")) {
                return false;
            }
        }
        return !TYPE_OTHER.equals(getFileType(normalised));
    }

    public String getFileType(String filePath) {
        String ext = getExtension(filePath);
        if (ext == null) return TYPE_OTHER;
        if (props.getSync().getFileTypes().getDocument().contains(ext)) return TYPE_DOCUMENT;
        if (props.getSync().getFileTypes().getCode().contains(ext)) return TYPE_CODE;
        return TYPE_OTHER;
    }

    private String getExtension(String filePath) {
        int slash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        String name = slash >= 0 ? filePath.substring(slash + 1) : filePath;
        int dot = name.lastIndexOf('.');
        if (dot <= 0) return null;
        return name.substring(dot).toLowerCase();
    }
}
