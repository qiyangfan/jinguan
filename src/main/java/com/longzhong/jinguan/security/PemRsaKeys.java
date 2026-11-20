package com.longzhong.jinguan.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** RSA PEM 解析:仅支持 PKCS#8 私钥与 X.509 公钥(不引 BouncyCastle);kid 取公钥指纹。 */
final class PemRsaKeys {

  private static final int MIN_RSA_BITS = 2048;

  private PemRsaKeys() {}

  /** 解析 PEM 文件;requirePrivate 时必须是私钥。 */
  static RSAKey read(Path file, boolean requirePrivate) {
    String pem = readPem(file);
    return toRsaKey(pemBody(pem, file), isPrivatePem(pem, requirePrivate, file), file);
  }

  /** 以 RFC 7638 公钥指纹补 kid(已有 kid 保留)。 */
  static RSAKey withKid(RSAKey key) {
    if (key.getKeyID() != null) {
      return key;
    }
    try {
      return new RSAKey.Builder(key).keyID(key.computeThumbprint().toString()).build();
    } catch (JOSEException e) {
      throw new IllegalStateException("无法计算密钥指纹", e);
    }
  }

  private static String readPem(Path file) {
    try {
      return Files.readString(file, StandardCharsets.US_ASCII);
    } catch (IOException e) {
      throw new IllegalStateException("签名密钥不可读: " + file, e);
    }
  }

  private static boolean isPrivatePem(String pem, boolean requirePrivate, Path file) {
    if (pem.contains("BEGIN RSA PRIVATE KEY")) {
      throw new IllegalStateException("须使用 PKCS#8 私钥(openssl pkcs8 -topk8 转换): " + file);
    }
    if (pem.contains("BEGIN ENCRYPTED PRIVATE KEY")) {
      throw new IllegalStateException("不支持加密私钥,请提供未加密 PKCS#8: " + file);
    }
    boolean privatePem = pem.contains("BEGIN PRIVATE KEY");
    if (requirePrivate && !privatePem) {
      throw new IllegalStateException("签名密钥必须是 RSA 私钥: " + file);
    }
    return privatePem;
  }

  private static RSAKey toRsaKey(byte[] der, boolean privatePem, Path file) {
    try {
      KeyFactory factory = KeyFactory.getInstance("RSA");
      if (privatePem) {
        RSAPrivateCrtKey privateKey =
            (RSAPrivateCrtKey) factory.generatePrivate(new PKCS8EncodedKeySpec(der));
        RSAPublicKey publicKey =
            (RSAPublicKey)
                factory.generatePublic(
                    new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
        return withKid(
            requireStrong(new RSAKey.Builder(publicKey).privateKey(privateKey).build(), file));
      }
      return withKid(
          requireStrong(
              new RSAKey.Builder((RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(der)))
                  .build(),
              file));
    } catch (GeneralSecurityException | ClassCastException e) {
      throw new IllegalStateException("签名密钥必须是 RSA PKCS#8/X.509 PEM: " + file, e);
    }
  }

  private static RSAKey requireStrong(RSAKey key, Path file) {
    if (key.size() < MIN_RSA_BITS) {
      throw new IllegalStateException("RSA 密钥至少 2048 位: " + file);
    }
    return key;
  }

  private static byte[] pemBody(String pem, Path file) {
    StringBuilder body = new StringBuilder();
    for (String line : pem.split("\\R")) {
      if (!line.startsWith("-----") && !line.isBlank()) {
        body.append(line.trim());
      }
    }
    try {
      return Base64.getDecoder().decode(body.toString());
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("PEM Base64 无法解码: " + file, e);
    }
  }
}
