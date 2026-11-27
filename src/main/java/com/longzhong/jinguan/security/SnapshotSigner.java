package com.longzhong.jinguan.security;

import com.nimbusds.jose.JOSEException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.util.Base64;
import org.springframework.stereotype.Component;

/** 控制快照签名:用 active RSA 私钥 RS256 签名响应体,服务经 JWKS 公钥验签。 */
@Component
public class SnapshotSigner {

  private final SigningKeys signingKeys;

  public SnapshotSigner(SigningKeys signingKeys) {
    this.signingKeys = signingKeys;
  }

  public String sign(String payloadJson) {
    try {
      Signature signature = Signature.getInstance("SHA256withRSA");
      signature.initSign(signingKeys.active().toRSAPrivateKey());
      signature.update(payloadJson.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(signature.sign());
    } catch (GeneralSecurityException | JOSEException e) {
      throw new IllegalStateException("快照签名失败", e);
    }
  }

  public String keyId() {
    return signingKeys.active().getKeyID();
  }
}
