package com.example.chatagent.tool.webops;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
public class SeoService {

    public Map<String, Object> audit(String url) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("url", url);
        try {
            Document doc = Jsoup.connect(url).userAgent("Mozilla/5.0 MCP-SEO-Bot").timeout(10_000).get();
            String title = doc.title();
            String metaDesc = doc.select("meta[name=description]").attr("content");
            int h1Count = doc.select("h1").size();
            int imgTotal = doc.select("img").size();
            int imgMissingAlt = doc.select("img:not([alt]), img[alt='']").size();

            out.put("title", title);
            out.put("title_ok", title != null && !title.isBlank() && title.length() <= 70);
            out.put("meta_description", metaDesc);
            out.put("meta_description_ok",
                    metaDesc != null && metaDesc.length() >= 50 && metaDesc.length() <= 160);
            out.put("h1_count", h1Count);
            out.put("h1_ok", h1Count == 1);
            out.put("images_total", imgTotal);
            out.put("images_missing_alt", imgMissingAlt);
            out.put("images_alt_ok", imgMissingAlt == 0);
            int score = 0;
            if ((boolean) out.get("title_ok")) score += 25;
            if ((boolean) out.get("meta_description_ok")) score += 25;
            if ((boolean) out.get("h1_ok")) score += 25;
            if ((boolean) out.get("images_alt_ok")) score += 25;
            out.put("score", score);
        } catch (Exception e) {
            log.warn("SEO audit failed for {}: {}", url, e.getMessage());
            out.put("error", e.getMessage());
        }
        return out;
    }
}
