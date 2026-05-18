package com.example.chatagent.config;

import com.example.chatagent.model.github.GitHubRepositoryConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Binds the {@code github.*} block in {@code application.yaml} into a
 * type-safe tree consumed by the GitHub sync + retrieval services.
 */
@ConfigurationProperties(prefix = "github")
public class GitHubProperties {

    private String token = "";
    private List<GitHubRepositoryConfig> repositories = new ArrayList<>();
    private Sync sync = new Sync();
    private Retrieval retrieval = new Retrieval();

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public List<GitHubRepositoryConfig> getRepositories() { return repositories; }
    public void setRepositories(List<GitHubRepositoryConfig> repositories) { this.repositories = repositories; }

    public Sync getSync() { return sync; }
    public void setSync(Sync sync) { this.sync = sync; }

    public Retrieval getRetrieval() { return retrieval; }
    public void setRetrieval(Retrieval retrieval) { this.retrieval = retrieval; }

    public static class Sync {
        private boolean enabled = false;
        private String cron = "0 0 2 * * *";
        private long maxFileSize = 200_000L;
        private FileTypes fileTypes = new FileTypes();
        private List<String> excludeDirectories = new ArrayList<>();
        private Chunking chunking = new Chunking();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getCron() { return cron; }
        public void setCron(String cron) { this.cron = cron; }

        public long getMaxFileSize() { return maxFileSize; }
        public void setMaxFileSize(long maxFileSize) { this.maxFileSize = maxFileSize; }

        public FileTypes getFileTypes() { return fileTypes; }
        public void setFileTypes(FileTypes fileTypes) { this.fileTypes = fileTypes; }

        public List<String> getExcludeDirectories() { return excludeDirectories; }
        public void setExcludeDirectories(List<String> excludeDirectories) { this.excludeDirectories = excludeDirectories; }

        public Chunking getChunking() { return chunking; }
        public void setChunking(Chunking chunking) { this.chunking = chunking; }
    }

    public static class FileTypes {
        private List<String> document = new ArrayList<>();
        private List<String> code = new ArrayList<>();

        public List<String> getDocument() { return document; }
        public void setDocument(List<String> document) { this.document = document; }

        public List<String> getCode() { return code; }
        public void setCode(List<String> code) { this.code = code; }
    }

    public static class Chunking {
        private DocumentChunking document = new DocumentChunking();
        private CodeChunking code = new CodeChunking();

        public DocumentChunking getDocument() { return document; }
        public void setDocument(DocumentChunking document) { this.document = document; }

        public CodeChunking getCode() { return code; }
        public void setCode(CodeChunking code) { this.code = code; }
    }

    public static class DocumentChunking {
        private int maxTokens = 800;
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    }

    public static class CodeChunking {
        private int maxLines = 120;
        public int getMaxLines() { return maxLines; }
        public void setMaxLines(int maxLines) { this.maxLines = maxLines; }
    }

    public static class Retrieval {
        private int topK = 5;
        private double similarityThreshold = 0.30;

        public int getTopK() { return topK; }
        public void setTopK(int topK) { this.topK = topK; }

        public double getSimilarityThreshold() { return similarityThreshold; }
        public void setSimilarityThreshold(double similarityThreshold) { this.similarityThreshold = similarityThreshold; }
    }
}
