package com.streaming.memberapi.discovery.datafetcher;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoveryDataFetcherTest {

    private final DiscoveryDataFetcher dataFetcher = new DiscoveryDataFetcher();

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> components(Map<String, Object> screen) {
        return (List<Map<String, Object>>) screen.get("components");
    }

    @Test
    void homeScreen_returnsComponentsAndVersion() {
        Map<String, Object> screen = dataFetcher.homeScreen(
            UUID.randomUUID().toString(), Map.of("platform", "WEB"));

        assertThat(screen).containsKey("components");
        assertThat(screen.get("version")).isEqualTo("2026.06.16");

        List<Map<String, Object>> components = components(screen);
        assertThat(components).isNotEmpty();
        // First component is always the hero
        assertThat(components.get(0).get("__typename")).isEqualTo("HeroComponent");
        // Contains a billboard somewhere
        assertThat(components).anyMatch(c -> "BillboardComponent".equals(c.get("__typename")));
    }

    @Test
    void homeScreen_tvPlatformGetsTvTabs() {
        Map<String, Object> screen = dataFetcher.homeScreen("m1", Map.of("platform", "TV"));

        Map<String, Object> tabs = components(screen).stream()
            .filter(c -> "TabComponent".equals(c.get("__typename")))
            .findFirst()
            .orElseThrow();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tabList = (List<Map<String, Object>>) tabs.get("tabs");
        assertThat(tabList).anyMatch(t -> "games".equals(t.get("id")));
    }

    @Test
    void homeScreen_webPlatformGetsWebTabs() {
        Map<String, Object> screen = dataFetcher.homeScreen("m1", Map.of("platform", "WEB"));

        Map<String, Object> tabs = components(screen).stream()
            .filter(c -> "TabComponent".equals(c.get("__typename")))
            .findFirst()
            .orElseThrow();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tabList = (List<Map<String, Object>>) tabs.get("tabs");
        assertThat(tabList).anyMatch(t -> "downloads".equals(t.get("id")));
        assertThat(tabList).noneMatch(t -> "games".equals(t.get("id")));
    }

    @Test
    void homeScreen_defaultsToWebWhenContextNull() {
        Map<String, Object> screen = dataFetcher.homeScreen("m1", null);

        Map<String, Object> tabs = components(screen).stream()
            .filter(c -> "TabComponent".equals(c.get("__typename")))
            .findFirst()
            .orElseThrow();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tabList = (List<Map<String, Object>>) tabs.get("tabs");
        assertThat(tabList).anyMatch(t -> "downloads".equals(t.get("id")));
    }

    @Test
    void browseScreen_usesGenreInRowLabel() {
        Map<String, Object> screen = dataFetcher.browseScreen("m1", "Horror", Map.of());

        assertThat(screen.get("genre")).isEqualTo("Horror");
        Map<String, Object> row = components(screen).get(0);
        assertThat(row.get("__typename")).isEqualTo("RowComponent");
        assertThat((String) row.get("label")).contains("Horror");
    }

    @Test
    void browseScreen_handlesNullGenre() {
        Map<String, Object> screen = dataFetcher.browseScreen("m1", null, Map.of());

        assertThat(screen.get("genre")).isEqualTo("");
        Map<String, Object> row = components(screen).get(0);
        assertThat((String) row.get("label")).contains("All");
    }
}
