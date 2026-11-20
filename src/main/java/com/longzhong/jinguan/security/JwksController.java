package com.longzhong.jinguan.security;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 发布 JWKS 公钥集供 RP 验签;只含公钥,不含任何私钥材料。 */
@RestController
public class JwksController {

  private final JWKSource<SecurityContext> jwkSource;

  public JwksController(JWKSource<SecurityContext> jwkSource) {
    this.jwkSource = jwkSource;
  }

  /** RFC 7517 JWK Set;RP 按 kid 选中公钥验签。 */
  @GetMapping("/.well-known/jwks.json")
  public Map<String, Object> jwks() {
    try {
      List<JWK> keys =
          jwkSource.get(new JWKSelector(new JWKMatcher.Builder().build()), null).stream()
              .map(JWK::toPublicJWK)
              .filter(jwk -> jwk != null)
              .toList();
      return new JWKSet(keys).toJSONObject();
    } catch (Exception e) {
      throw new IllegalStateException("无法构建 JWKS", e);
    }
  }
}
