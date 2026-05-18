package com.example.chatagent.service.github;

import com.example.chatagent.config.GitHubProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits raw file content into embedding-sized chunks.
 *
 * <ul>
 *   <li>Documents (Markdown / text): paragraph-aware split, soft-capped by
 *       an approximate token count (~4 chars per token).</li>
 *   <li>Code: sliding line windows with overlap so function/class boundaries
 *       still appear in at least one chunk.</li>
 * </ul>
 */
@Service
public class GitHubChunkingService {

    private static final int CODE_OVERLAP_LINES = 20;
    private static final int CHARS_PER_TOKEN = 4;

    private final GitHubProperties props;

    public GitHubChunkingService(GitHubProperties props) {
        this.props = props;
    }

    public List<String> chunk(String content, String fileType) {
        if (content == null || content.isBlank()) return List.of();
        if (GitHubFileFilter.TYPE_CODE.equals(fileType)) {
            return chunkCode(content);
        }
        return chunkDocument(content);
    }

    public List<String> chunkDocument(String content) {
        int maxChars = props.getSync().getChunking().getDocument().getMaxTokens() * CHARS_PER_TOKEN;
        List<String> chunks = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        // split by blank lines so we keep paragraph integrity
        for (String para : content.split("\\n\\s*\\n")) {
            if (para.isBlank()) continue;
            if (buf.length() + para.length() + 2 > maxChars && buf.length() > 0) {
                chunks.add(buf.toString().trim());
                buf.setLength(0);
            }
            if (para.length() > maxChars) {
                // single oversized paragraph — hard-split
                int start = 0;
                while (start < para.length()) {
                    int end = Math.min(start + maxChars, para.length());
                    chunks.add(para.substring(start, end).trim());
                    start = end;
                }
            } else {
                buf.append(para).append("\n\n");
            }
        }
        if (buf.length() > 0) chunks.add(buf.toString().trim());
        return chunks;
    }

    public List<String> chunkCode(String content) {
        int maxLines = props.getSync().getChunking().getCode().getMaxLines();
        String[] lines = content.split("\\n", -1);
        if (lines.length <= maxLines) {
            return List.of(content);
        }
        List<String> chunks = new ArrayList<>();
        int step = Math.max(1, maxLines - CODE_OVERLAP_LINES);
        for (int start = 0; start < lines.length; start += step) {
            int end = Math.min(start + maxLines, lines.length);
            StringBuilder buf = new StringBuilder();
            buf.append("// lines ").append(start + 1).append('-').append(end).append('\n');
            for (int i = start; i < end; i++) {
                buf.append(lines[i]).append('\n');
            }
            chunks.add(buf.toString());
            if (end >= lines.length) break;
        }
        return chunks;
    }
}
