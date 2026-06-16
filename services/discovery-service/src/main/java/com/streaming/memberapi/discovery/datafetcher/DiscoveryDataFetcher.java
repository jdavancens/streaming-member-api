package com.streaming.memberapi.discovery.datafetcher;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SDUI: Server decides what UI components to render for each screen.
 * In production this would call a recommendations/personalization backend.
 * Here we return a deterministic stub to demonstrate the component union pattern.
 */
@DgsComponent
public class DiscoveryDataFetcher {

    @DgsQuery
    public Map<String, Object> homeScreen(@InputArgument String memberId,
                                          @InputArgument Map<String, Object> context) {
        String platform = context != null ? (String) context.get("platform") : "WEB";

        List<Object> components = buildHomeComponents(memberId, platform);
        return Map.of(
            "components", components,
            "version", "2026.06.16"
        );
    }

    @DgsQuery
    public Map<String, Object> browseScreen(@InputArgument String memberId,
                                             @InputArgument String genre,
                                             @InputArgument Map<String, Object> context) {
        return Map.of(
            "components", List.of(buildRow("Top Picks in " + (genre != null ? genre : "All"), memberId)),
            "genre", genre != null ? genre : ""
        );
    }

    private List<Object> buildHomeComponents(String memberId, String platform) {
        return List.of(
            // Hero — drives the "above the fold" experience
            Map.of(
                "__typename", "HeroComponent",
                "id", UUID.randomUUID().toString(),
                "title", "Stranger Things",
                "backgroundImageUrl", "https://cdn.example.com/stranger-things-bg.jpg",
                "logoImageUrl", "https://cdn.example.com/stranger-things-logo.png",
                "ctaLabel", "Continue Watching",
                "ctaContentId", "tt4574334",
                "description", "A love letter to the classic horror films of the 80s."
            ),
            // Tabs — platform-driven navigation (TV shows different tabs than Mobile)
            buildTabs(platform),
            // Personalised row
            buildRow("Continue Watching", memberId),
            buildRow("New Releases", memberId),
            buildRow("Because you watched Stranger Things", memberId),
            // Billboard for a promotion
            Map.of(
                "__typename", "BillboardComponent",
                "id", UUID.randomUUID().toString(),
                "headline", "Games. Now on Netflix.",
                "subtext", "Included with your membership at no extra cost.",
                "actions", List.of(
                    Map.of("label", "Play Now", "contentId", "game-001", "style", "PRIMARY"),
                    Map.of("label", "Learn More", "contentId", "games-info", "style", "SECONDARY")
                )
            )
        );
    }

    private Map<String, Object> buildRow(String label, String memberId) {
        return Map.of(
            "__typename", "RowComponent",
            "id", UUID.randomUUID().toString(),
            "label", label,
            "items", List.of(
                Map.of("id", "tt4574334", "title", "Stranger Things",
                       "thumbnailUrl", "https://cdn.example.com/st-thumb.jpg",
                       "type", "SERIES", "durationSeconds", 0, "matchScore", 98),
                Map.of("id", "tt2085059", "title", "Black Mirror",
                       "thumbnailUrl", "https://cdn.example.com/bm-thumb.jpg",
                       "type", "SERIES", "durationSeconds", 0, "matchScore", 94),
                Map.of("id", "tt6468322", "title", "The Crown",
                       "thumbnailUrl", "https://cdn.example.com/crown-thumb.jpg",
                       "type", "SERIES", "durationSeconds", 0, "matchScore", 91)
            )
        );
    }

    private Map<String, Object> buildTabs(String platform) {
        List<Map<String, Object>> tabs = "TV".equals(platform)
            ? List.of(
                Map.of("id", "home", "label", "Home", "selected", true),
                Map.of("id", "movies", "label", "Movies", "selected", false),
                Map.of("id", "shows", "label", "TV Shows", "selected", false),
                Map.of("id", "games", "label", "Games", "selected", false)
              )
            : List.of(
                Map.of("id", "home", "label", "Home", "selected", true),
                Map.of("id", "coming-soon", "label", "Coming Soon", "selected", false),
                Map.of("id", "downloads", "label", "Downloads", "selected", false)
              );

        return Map.of(
            "__typename", "TabComponent",
            "id", UUID.randomUUID().toString(),
            "tabs", tabs
        );
    }
}
