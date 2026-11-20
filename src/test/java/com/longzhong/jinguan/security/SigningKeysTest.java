package com.longzhong.jinguan.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** 签名密钥库:挂载目录加载、退役公钥保留验签、未挂载退化临时密钥、配置错误快速失败。 */
class SigningKeysTest {

  private static final String ACTIVE_FILE = "active.pem";
  private static final String RETIRED_DIR = "retired";
  private static final String OLD_KEY_FILE = "old-key.pem";
  private static final int KEY_BITS = 2048;
  private static final String PRIVATE_KEY_TYPE = "PRIVATE KEY";

  @TempDir Path keysDir;

  @Test
  void mountedDirPublishesActiveAndRetired() throws Exception {
    KeyPair activePair = generatePair(KEY_BITS);
    writePem(keysDir.resolve(ACTIVE_FILE), PRIVATE_KEY_TYPE, activePair.getPrivate().getEncoded());
    writePem(
        Files.createDirectories(keysDir.resolve(RETIRED_DIR)).resolve(OLD_KEY_FILE),
        "PUBLIC KEY",
        generatePair(KEY_BITS).getPublic().getEncoded());

    SigningKeys first = SigningKeys.load(keysDir.toString());
    SigningKeys second = SigningKeys.load(keysDir.toString());

    assertEquals(2, first.jwkSet().getKeys().size(), "JWKS 必须同时含 active 与退役公钥");
    assertEquals(first.active().getKeyID(), second.active().getKeyID(), "kid 必须跨加载稳定(指纹派生)");
    RSAKey retired =
        first.jwkSet().getKeys().stream()
            .map(RSAKey.class::cast)
            .filter(key -> !key.getKeyID().equals(first.active().getKeyID()))
            .findFirst()
            .orElseThrow();
    assertFalse(retired.isPrivate(), "退役公钥不得涉及私钥");
  }

  @Test
  void tokenSignedByRetiredKeyStillVerifies() throws Exception {
    writePem(
        keysDir.resolve(ACTIVE_FILE),
        PRIVATE_KEY_TYPE,
        generatePair(KEY_BITS).getPrivate().getEncoded());
    writePem(
        Files.createDirectories(keysDir.resolve(RETIRED_DIR)).resolve(OLD_KEY_FILE),
        PRIVATE_KEY_TYPE,
        generatePair(KEY_BITS).getPrivate().getEncoded());

    SigningKeys keys = SigningKeys.load(keysDir.toString());
    JwtDecoder decoder = new JwtCryptoConfig().jwtDecoder(new ImmutableJWKSet<>(keys.jwkSet()));
    RSAKey retired =
        keys.jwkSet().getKeys().stream()
            .map(RSAKey.class::cast)
            .filter(key -> !key.getKeyID().equals(keys.active().getKeyID()))
            .findFirst()
            .orElseThrow();
    String token =
        new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(List.<JWK>of(retired))))
            .encode(
                JwtEncoderParameters.from(
                    JwsHeader.with(SignatureAlgorithm.RS256).build(), claims()))
            .getTokenValue();

    Jwt decoded = decoder.decode(token);
    assertEquals(retired.getKeyID(), decoded.getHeaders().get("kid"), "退役键签发的 token 必须仍可验签");
    assertEquals("old-user", decoded.getSubject(), "claims 必须原样解出");
  }

  @Test
  void unsetDirFallsBackToEphemeral() {
    SigningKeys keys = SigningKeys.load("  ");

    assertEquals(1, keys.jwkSet().getKeys().size(), "未挂载时应只有临时签名键");
    assertTrue(keys.active().isPrivate(), "临时键必须含私钥");
    assertEquals(43, keys.active().getKeyID().length(), "kid 必须是 SHA-256 指纹(43 字符)");
  }

  @Test
  void missingActiveKeyFailsFast() {
    IllegalStateException error =
        assertThrows(IllegalStateException.class, () -> SigningKeys.load(keysDir.toString()));

    assertTrue(error.getMessage().contains(ACTIVE_FILE), "失败信息必须指出缺失的 active.pem");
  }

  @Test
  void publicKeyInActiveSlotIsRejected() throws Exception {
    writePem(
        keysDir.resolve(ACTIVE_FILE),
        "PUBLIC KEY",
        generatePair(KEY_BITS).getPublic().getEncoded());

    IllegalStateException error =
        assertThrows(IllegalStateException.class, () -> SigningKeys.load(keysDir.toString()));

    assertTrue(error.getMessage().contains("私钥"), "active 槽位必须是私钥");
  }

  @Test
  void duplicateActiveKeyInRetiredIsIgnored() throws Exception {
    KeyPair pair = generatePair(KEY_BITS);
    writePem(keysDir.resolve(ACTIVE_FILE), PRIVATE_KEY_TYPE, pair.getPrivate().getEncoded());
    writePem(
        Files.createDirectories(keysDir.resolve(RETIRED_DIR)).resolve(OLD_KEY_FILE),
        PRIVATE_KEY_TYPE,
        pair.getPrivate().getEncoded());

    SigningKeys keys = SigningKeys.load(keysDir.toString());

    assertEquals(1, keys.jwkSet().getKeys().size(), "与 active 重复的退役键不得重复发布");
  }

  @Test
  void weakKeyIsRejected() throws Exception {
    writePem(
        keysDir.resolve(ACTIVE_FILE),
        PRIVATE_KEY_TYPE,
        generatePair(1024).getPrivate().getEncoded());

    IllegalStateException error =
        assertThrows(IllegalStateException.class, () -> SigningKeys.load(keysDir.toString()));

    assertTrue(error.getMessage().contains("2048"), "弱密钥必须拒绝并说明位数下限");
  }

  private static KeyPair generatePair(int bits) throws NoSuchAlgorithmException {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(bits);
    return generator.generateKeyPair();
  }

  private static void writePem(Path file, String type, byte[] encoded) throws IOException {
    String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded);
    Files.writeString(
        file,
        "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n",
        StandardCharsets.US_ASCII);
  }

  private static JwtClaimsSet claims() {
    Instant now = Instant.now();
    return JwtClaimsSet.builder()
        .subject("old-user")
        .issuedAt(now)
        .expiresAt(now.plusSeconds(60))
        .build();
  }
}
