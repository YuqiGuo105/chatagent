package com.example.chatagent.tool.webops;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.Map;

/**
 * In-process Spring AI tool for Portfolio web operations (blogs, SEO, performance).
 * Wired by {@link com.example.chatagent.config.InProcessToolsConfig}.
 */
public class WebOpsTool {

    private final BlogService blogService;
    private final SeoService seoService;
    private final PerformanceService performanceService;

    public WebOpsTool(BlogService blogService, SeoService seoService,
                      PerformanceService performanceService) {
        this.blogService = blogService;
        this.seoService = seoService;
        this.performanceService = performanceService;
    }

    // ---- Tech blogs ----

    @Tool(name = "list_tech_blogs",
          description = "List all tech/portfolio blog posts from the 'Blogs' table (id, title, date, category, tags).")
    public List<Map<String, Object>> listTechBlogs() {
        return blogService.listTechBlogs();
    }

    @Tool(name = "create_tech_blog",
          description = "Create a new tech/portfolio blog post in the 'Blogs' table. Returns the generated UUID.")
    public Map<String, Object> createTechBlog(
            @ToolParam(description = "Blog title.") String title,
            @ToolParam(description = "Publication date (YYYY-MM-DD).", required = false) String date,
            @ToolParam(description = "Short description / excerpt.") String description,
            @ToolParam(description = "Category label.", required = false) String category,
            @ToolParam(description = "Cover image URL.", required = false) String imageUrl,
            @ToolParam(description = "Full Markdown / HTML content.") String content,
            @ToolParam(description = "Comma-separated tags.", required = false) String tags) {
        return blogService.createTechBlog(title, date, description, category, imageUrl, content, tags);
    }

    @Tool(name = "update_tech_blog",
          description = "Update a tech/portfolio blog post by its UUID.")
    public Map<String, Object> updateTechBlog(
            @ToolParam(description = "UUID of the blog entry.") String id,
            @ToolParam(description = "New title.", required = false) String title,
            @ToolParam(description = "New description.", required = false) String description,
            @ToolParam(description = "New content.", required = false) String content,
            @ToolParam(description = "New tags.", required = false) String tags,
            @ToolParam(description = "New category.", required = false) String category,
            @ToolParam(description = "New cover image URL.", required = false) String imageUrl) {
        return blogService.updateTechBlog(id, title, description, content, tags, category, imageUrl);
    }

    @Tool(name = "delete_tech_blog",
          description = "Delete a tech/portfolio blog post by its UUID.")
    public Map<String, Object> deleteTechBlog(
            @ToolParam(description = "UUID of the blog entry.") String id) {
        return blogService.deleteTechBlog(id);
    }

    // ---- Life blogs ----

    @Tool(name = "list_life_blogs",
          description = "List all life/personal blog posts from the life_blogs table.")
    public List<Map<String, Object>> listLifeBlogs() {
        return blogService.listLifeBlogs();
    }

    @Tool(name = "create_life_blog",
          description = "Create a new life/personal blog post in the life_blogs table.")
    public Map<String, Object> createLifeBlog(
            @ToolParam(description = "Blog title.") String title,
            @ToolParam(description = "Cover image URL.", required = false) String imageUrl,
            @ToolParam(description = "Category label.", required = false) String category,
            @ToolParam(description = "Publication date (YYYY-MM-DD).", required = false) String publishedAt,
            @ToolParam(description = "Short description / excerpt.", required = false) String description,
            @ToolParam(description = "Whether this post requires login. Default false.", required = false) Boolean requireLogin,
            @ToolParam(description = "Comma-separated tags.", required = false) String tags,
            @ToolParam(description = "Full Markdown / HTML content.") String content) {
        return blogService.createLifeBlog(title, imageUrl, category, publishedAt,
                description, requireLogin, tags, content);
    }

    @Tool(name = "update_life_blog",
          description = "Update a life/personal blog post by its integer id.")
    public Map<String, Object> updateLifeBlog(
            @ToolParam(description = "Integer id of the life blog entry.") Integer id,
            @ToolParam(description = "New title.", required = false) String title,
            @ToolParam(description = "New description.", required = false) String description,
            @ToolParam(description = "New content.", required = false) String content,
            @ToolParam(description = "New tags.", required = false) String tags,
            @ToolParam(description = "New category.", required = false) String category,
            @ToolParam(description = "New cover image URL.", required = false) String imageUrl,
            @ToolParam(description = "Toggle require_login flag.", required = false) Boolean requireLogin) {
        return blogService.updateLifeBlog(id, title, description, content, tags,
                category, imageUrl, requireLogin);
    }

    @Tool(name = "delete_life_blog",
          description = "Delete a life/personal blog post by its integer id.")
    public Map<String, Object> deleteLifeBlog(
            @ToolParam(description = "Integer id of the life blog entry.") Integer id) {
        return blogService.deleteLifeBlog(id);
    }

    // ---- Site health ----

    @Tool(name = "seo_audit",
          description = "Audit a public URL for SEO: title tag, meta description, H1 count, image alt text. Returns a score out of 100.")
    public Map<String, Object> seoAudit(
            @ToolParam(description = "Public URL to audit.") String url) {
        return seoService.audit(url);
    }

    @Tool(name = "performance_check",
          description = "Measure page load time and HTTP status for a public URL.")
    public Map<String, Object> performanceCheck(
            @ToolParam(description = "Public URL to measure.") String url) {
        return performanceService.check(url);
    }

    @Tool(name = "get_analytics",
          description = "Return Portfolio content analytics: total and recent blog counts for both 'Blogs' and life_blogs tables.")
    public Map<String, Object> getAnalytics() {
        return blogService.analyticsSummary();
    }

    @Tool(name = "backup_database",
          description = "Returns the recommended pg_dump command for a manual Portfolio DB backup.")
    public Map<String, Object> backupDatabase() {
        return Map.of(
                "status", "manual",
                "command", "pg_dump $PORTFOLIO_DB_URL > backup-$(date +%F).sql",
                "note", "Configure automated backups at the Supabase / infrastructure level.");
    }
}
