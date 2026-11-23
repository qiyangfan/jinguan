package com.longzhong.jinguan.oidc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** OIDC state cookie 的有界多值编码:点分隔、去重、旧单值兼容。 */
final class OidcStateCookie {

  static final int MAX_STATES = 8;
  private static final String SEPARATOR = ".";

  private OidcStateCookie() {}

  /** 解析 cookie 值为 state 列表;空段过滤,旧单值返回单元素。 */
  static List<String> parse(String raw) {
    List<String> states = new ArrayList<>();
    if (raw == null || raw.isEmpty()) {
      return states;
    }
    for (String part : raw.split("\\.", -1)) {
      if (!part.isEmpty()) {
        states.add(part);
      }
    }
    return states;
  }

  /** 去重保序,最多保留最近 MAX_STATES 个;空列表返回空串。 */
  static String encode(List<String> states) {
    Set<String> unique = new LinkedHashSet<>();
    for (String state : states) {
      if (state != null && !state.isEmpty()) {
        unique.add(state);
      }
    }
    List<String> ordered = new ArrayList<>(unique);
    if (ordered.size() > MAX_STATES) {
      ordered = new ArrayList<>(ordered.subList(ordered.size() - MAX_STATES, ordered.size()));
    }
    return String.join(SEPARATOR, ordered);
  }

  /** 恒定时间判断列表是否包含目标 state。 */
  static boolean contains(List<String> states, String state) {
    if (state == null) {
      return false;
    }
    byte[] target = state.getBytes(StandardCharsets.UTF_8);
    boolean found = false;
    for (String candidate : states) {
      // 不短路:比较次数只与列表长度相关
      if (MessageDigest.isEqual(candidate.getBytes(StandardCharsets.UTF_8), target)) {
        found = true;
      }
    }
    return found;
  }

  /** 移除已消费 state,保留其余 tab 的 state。 */
  static List<String> remove(List<String> states, String state) {
    if (state == null) {
      return new ArrayList<>(states);
    }
    byte[] target = state.getBytes(StandardCharsets.UTF_8);
    List<String> remaining = new ArrayList<>();
    for (String candidate : states) {
      if (!MessageDigest.isEqual(candidate.getBytes(StandardCharsets.UTF_8), target)) {
        remaining.add(candidate);
      }
    }
    return remaining;
  }
}
