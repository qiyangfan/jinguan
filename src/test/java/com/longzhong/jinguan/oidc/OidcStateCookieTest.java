package com.longzhong.jinguan.oidc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** OIDC state cookie 多值编码:旧单值兼容、去重、上限与精确匹配。 */
class OidcStateCookieTest {

  @Test
  void legacySingleValueParsesAsOneState() {
    assertEquals(List.of("legacy-state"), OidcStateCookie.parse("legacy-state"), "旧单值应解析为单元素");
    assertEquals(List.of("a", "b"), OidcStateCookie.parse("a..b"), "空段必须过滤");
  }

  @Test
  void encodeDeduplicatesAndKeepsNewestEight() {
    List<String> states = IntStream.range(0, 10).mapToObj(i -> "s" + i).toList();
    assertEquals("s2.s3.s4.s5.s6.s7.s8.s9", OidcStateCookie.encode(states), "超出上限应丢最旧");
    assertEquals("a.b", OidcStateCookie.encode(List.of("a", "b", "a")), "重复 state 必须去重");
  }

  @Test
  void containsAndRemoveOnlyMatchExactState() {
    List<String> states = List.of("a", "b");
    assertTrue(OidcStateCookie.contains(states, "a"), "已绑定 state 应命中");
    assertFalse(OidcStateCookie.contains(states, "c"), "未绑定 state 不得命中");
    assertEquals(List.of("b"), OidcStateCookie.remove(states, "a"), "只移除已消费 state");
    assertEquals(List.of("a", "b"), OidcStateCookie.remove(states, "c"), "未命中时列表不变");
  }
}
