package com.example.chatagent.model.github;

/**
 * One entry in {@code github.repositories} — identifies a repo + branch to sync.
 */
public class GitHubRepositoryConfig {
    private String owner;
    private String name;
    private String branch = "main";

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }

    @Override
    public String toString() {
        return owner + "/" + name + "@" + branch;
    }
}
