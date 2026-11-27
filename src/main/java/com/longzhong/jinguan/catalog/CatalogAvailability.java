package com.longzhong.jinguan.catalog;

import com.longzhong.jinguan.projection.ProjectionService;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** 目录引用可用性:来自 kongming 只读投影的 bot 清单;投影缺失时不猜测。 */
@Component
class CatalogAvailability {

  private static final String KONGMING = "kongming";

  private final ProjectionService projections;

  CatalogAvailability(ProjectionService projections) {
    this.projections = projections;
  }

  Index load() {
    var projection = projections.get(KONGMING);
    if (!"FRESH".equals(projection.state()) && !"STALE".equals(projection.state())) {
      return new Index(false, Set.of());
    }
    JsonNode bots = projection.sections() == null ? null : projection.sections().path("bots");
    if (bots == null || !bots.isArray()) {
      return new Index(false, Set.of());
    }
    Set<String> keys = new HashSet<>();
    bots.forEach(
        bot -> {
          JsonNode key = bot.path("bot_key");
          if (key.isTextual()) {
            keys.add(key.asString());
          }
        });
    return new Index(true, Set.copyOf(keys));
  }

  CatalogViews.Availability of(String botKey, Index index) {
    if (!index.known()) {
      return CatalogViews.Availability.UNKNOWN;
    }
    return index.botKeys().contains(botKey)
        ? CatalogViews.Availability.AVAILABLE
        : CatalogViews.Availability.MISSING;
  }

  /** 一次请求共享的可用性索引;known=false 时键集无意义。 */
  record Index(boolean known, Set<String> botKeys) {}
}
