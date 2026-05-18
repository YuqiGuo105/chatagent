package com.example.chatagent.model.github;

/**
 * Lightweight DTO representing one indexed chunk returned from
 * {@code github_project_documents} similarity search.
 */
public class GitHubProjectDocument {
    private Long id;
    private String repoOwner;
    private String repoName;
    private String branch;
    private String filePath;
    private String fileType;
    private String content;
    private Integer chunkIndex;
    private Double similarity;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRepoOwner() { return repoOwner; }
    public void setRepoOwner(String repoOwner) { this.repoOwner = repoOwner; }

    public String getRepoName() { return repoName; }
    public void setRepoName(String repoName) { this.repoName = repoName; }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }

    public Double getSimilarity() { return similarity; }
    public void setSimilarity(Double similarity) { this.similarity = similarity; }

    public String repoSlug() {
        return repoOwner + "/" + repoName;
    }
}
