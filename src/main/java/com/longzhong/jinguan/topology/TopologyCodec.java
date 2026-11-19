package com.longzhong.jinguan.topology;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** 拓扑负载的 JSON 编解码与 ETag 计算；数据损坏时拒绝下发。 */
@Component
class TopologyCodec {

  private final ObjectMapper mapper;

  TopologyCodec(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  <T> T[] deserialize(String json, Class<T[]> type) {
    try {
      return mapper.readValue(json, type);
    } catch (JacksonException e) {
      throw new IllegalStateException("拓扑 JSON 列损坏,读取被拒绝: " + e.getOriginalMessage(), e);
    }
  }

  String serialize(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JacksonException e) {
      throw new IllegalStateException("拓扑 JSON 序列化失败", e);
    }
  }

  String etagOf(TopologyService.TopologyPayload payload) {
    return quote(hashOf(payload));
  }

  private String hashOf(TopologyService.TopologyPayload payload) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(serialize(payload).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest, 0, 8);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 不可用", e);
    }
  }

  private String quote(String hex) {
    return "\"" + hex + "\"";
  }
}
