package com.longzhong.jinguan.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/** RS256 签发→JWKS 验签闭环:claims 往返一致;签发头 kid 与 JWKS 公钥一致。 */
class JwtCryptoIntegrationTest {

  private final JwtCryptoConfig config = new JwtCryptoConfig();
  private final SigningKeys signingKeys = SigningKeys.load("");
  private final JWKSource<SecurityContext> jwkSource = config.jwkSource(signingKeys);
  private final JwtEncoder encoder = config.jwtEncoder(signingKeys);
  private final JwtDecoder decoder = config.jwtDecoder(jwkSource);

  @Test
  void rs256ClaimsRoundTrip() {
    Jwt decoded = decoder.decode(sign(sampleClaims()));

    assertEquals("http://localhost:8090", decoded.getClaim("iss"), "iss 必须往返一致");
    assertEquals("42", decoded.getSubject(), "sub 必须往返一致");
    assertEquals(42L, ((Number) decoded.getClaim("subject")).longValue(), "subject 必须往返一致");
    assertEquals("ADMIN", decoded.getClaim("role"), "role 必须往返一致");
  }

  @Test
  void jwtHeaderKidMatchesJwksKey() throws Exception {
    Jwt decoded = decoder.decode(sign(sampleClaims()));

    Object kid = decoded.getHeaders().get("kid");
    assertNotNull(kid, "签名头必须带 kid");
    List<JWK> keys = jwkSource.get(new JWKSelector(new JWKMatcher.Builder().build()), null);
    assertEquals(1, keys.size(), "JWKS 应只含当前签名公钥");
    assertEquals(kid.toString(), keys.get(0).getKeyID(), "头 kid 必须对应 JWKS 公钥");
  }

  private String sign(JwtClaimsSet claims) {
    JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  private static JwtClaimsSet sampleClaims() {
    return JwtClaimsSet.builder()
        .issuer("http://localhost:8090")
        .audience(List.of("jinguan"))
        .subject("42")
        .claim("subject", 42L)
        .claim("role", "ADMIN")
        .claim("typ", "access")
        .build();
  }
}
