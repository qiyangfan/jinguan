package com.longzhong.jinguan.topology;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 检查预设适配器、HTTPS 地址、环境变量名和厂商名约束。 */
class ProviderPresetCatalogTest {

  /** muniu 支持的适配器类型。 */
  private static final Set<String> MUNIU_SUPPORTED_ADAPTERS =
      Set.of("openai", "qwen", "doubao", "deepseek", "moonshot", "glm", "anthropic");

  @Test
  void catalog_is_non_empty() {
    assertFalse(ProviderPresetCatalog.presets().isEmpty());
    assertTrue(ProviderPresetCatalog.presets().size() >= 15, "官方厂商预设应尽量多,当前少于 15 家");
  }

  @Test
  void every_preset_adapter_is_supported_by_muniu() {
    for (ProviderPresetCatalog.Preset p : ProviderPresetCatalog.presets()) {
      assertTrue(
          MUNIU_SUPPORTED_ADAPTERS.contains(p.adapter()),
          () -> "预设 %s 的 adapter %s 不在 muniu 支持集".formatted(p.name(), p.adapter()));
    }
  }

  @Test
  void base_urls_are_https_without_trailing_slash() {
    for (ProviderPresetCatalog.Preset p : ProviderPresetCatalog.presets()) {
      assertTrue(p.baseUrl().startsWith("https://"), () -> p.name() + " base_url 必须 https");
      assertFalse(p.baseUrl().endsWith("/"), () -> p.name() + " base_url 不带尾斜杠(拼接语义归一)");
    }
  }

  @Test
  void names_are_unique() {
    Set<String> seen = new HashSet<>();
    for (ProviderPresetCatalog.Preset p : ProviderPresetCatalog.presets()) {
      assertTrue(seen.add(p.name()), () -> "预设 name 重复:" + p.name());
    }
  }

  @Test
  void env_suggestions_are_upper_snake_api_key() {
    for (ProviderPresetCatalog.Preset p : ProviderPresetCatalog.presets()) {
      assertTrue(
          p.envSuggestion().matches("[A-Z0-9_]+_API_KEY"),
          () -> p.name() + " env 建议名须全大写下划线且以 _API_KEY 收尾");
    }
  }

  @Test
  void every_preset_has_models_website_region_note() {
    for (ProviderPresetCatalog.Preset p : ProviderPresetCatalog.presets()) {
      assertFalse(p.models().isEmpty(), () -> p.name() + " 应给建议模型");
      assertTrue(p.models().stream().allMatch(m -> !m.isBlank()), () -> p.name() + " 模型 ID 非空");
      assertTrue(p.website().startsWith("https://"), () -> p.name() + " 官网必须 https");
      assertTrue(List.of("cn", "global").contains(p.region()), () -> p.name() + " region 取值非法");
      assertFalse(p.note().isBlank(), () -> p.name() + " 应有备注");
      assertFalse(p.displayName().isBlank(), () -> p.name() + " 应有中文展示名");
    }
  }
}
