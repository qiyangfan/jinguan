package com.longzhong.jinguan.security;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** RS256 签发/验签与 JWKS 密钥源,替代 HS256 对称共享密钥。 对称体制下任何持密钥方均可伪造任意 token,故升级非对称;RP 以公钥(kid)验签。 */
@Configuration
public class JwtCryptoConfig {

  /** 密钥库:挂载目录(`active.pem` + `retired/*.pem`)优先,未配置退化为进程内临时密钥。 */
  @Bean
  public SigningKeys signingKeys(@Value("${jinguan.security.jwt-keys-dir:}") String keysDir) {
    return SigningKeys.load(keysDir);
  }

  /** JWKS 公钥集:active + 退役公钥,供 RP 与自身验签。 */
  @Bean
  public JWKSource<SecurityContext> jwkSource(SigningKeys signingKeys) {
    return new ImmutableJWKSet<>(signingKeys.jwkSet());
  }

  /** 仅以 active 私钥签发 RS256 token。 */
  @Bean
  public JwtEncoder jwtEncoder(SigningKeys signingKeys) {
    return new NimbusJwtEncoder(
        new ImmutableJWKSet<>(new JWKSet(List.<JWK>of(signingKeys.active()))));
  }

  /** 以全部公钥验签,按 kid 选中签发键(含退役键,旧 token 可用至过期)。 */
  @Bean
  public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
    return NimbusJwtDecoder.withJwkSource(jwkSource).build();
  }
}
