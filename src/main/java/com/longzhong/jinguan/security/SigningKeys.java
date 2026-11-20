package com.longzhong.jinguan.security;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 签名密钥库:优先加载挂载目录(`active.pem` PKCS#8 私钥 + `retired/*.pem` 退役公钥),未配置时退化为进程内临时密钥(仅开发)。 kid 取 RFC 7638
 * 公钥指纹,跨重启稳定;退役公钥持续经 JWKS 发布,直至其签发 token 全部过期。
 */
public final class SigningKeys {

  static final String ACTIVE_FILE = "active.pem";
  static final String RETIRED_DIR = "retired";

  private static final Logger log = LoggerFactory.getLogger(SigningKeys.class);

  private final RSAKey signingKey;
  private final JWKSet publishedKeys;

  private SigningKeys(RSAKey signingKey, List<RSAKey> retired) {
    this.signingKey = signingKey;
    List<JWK> live = new ArrayList<>();
    live.add(signingKey);
    live.addAll(retired);
    this.publishedKeys = new JWKSet(live);
  }

  /** 从挂载目录加载;目录未配置则生成进程内临时密钥。 */
  public static SigningKeys load(String keysDir) {
    if (keysDir == null || keysDir.isBlank()) {
      log.warn("未配置 jinguan.security.jwt-keys-dir,使用进程内临时签名密钥:重启即换钥、全部 token 失效,生产环境必须挂载密钥目录");
      return new SigningKeys(generate(), List.of());
    }
    Path root = Path.of(keysDir);
    RSAKey signing = PemRsaKeys.read(root.resolve(ACTIVE_FILE), true);
    List<RSAKey> retired = readRetired(root.resolve(RETIRED_DIR), signing.getKeyID());
    log.info("签名密钥已加载:active kid={},退役公钥 {} 枚", signing.getKeyID(), retired.size());
    return new SigningKeys(signing, retired);
  }

  /** 签发用私钥。 */
  public RSAKey active() {
    return signingKey;
  }

  /** 对外发布与验签的公钥集:active + 退役。 */
  public JWKSet jwkSet() {
    return publishedKeys;
  }

  private static List<RSAKey> readRetired(Path dir, String activeKid) {
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    try (Stream<Path> paths = Files.list(dir)) {
      Map<String, RSAKey> byKid = new LinkedHashMap<>();
      for (Path file : paths.filter(p -> p.toString().endsWith(".pem")).sorted().toList()) {
        RSAKey key = PemRsaKeys.read(file, false);
        if (key.getKeyID().equals(activeKid)) {
          log.warn("退役公钥与 active 相同(kid={}):{} 已忽略", activeKid, file);
        } else if (byKid.putIfAbsent(key.getKeyID(), key) != null) {
          log.warn("退役公钥重复(kid={}):{} 已忽略", key.getKeyID(), file);
        }
      }
      return List.copyOf(byKid.values());
    } catch (IOException e) {
      throw new UncheckedIOException("读取退役公钥目录失败: " + dir, e);
    }
  }

  private static RSAKey generate() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      KeyPair pair = generator.generateKeyPair();
      return PemRsaKeys.withKid(
          new RSAKey.Builder((RSAPublicKey) pair.getPublic())
              .privateKey((RSAPrivateKey) pair.getPrivate())
              .build());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("无法生成 RSA 签名密钥", e);
    }
  }
}
