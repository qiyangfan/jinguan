package com.longzhong.jinguan.security;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 生产密钥目录缺失必须拒启;开发默认允许临时密钥。 */
class JwtCryptoConfigTest {

  private final JwtCryptoConfig config = new JwtCryptoConfig();

  @Test
  void requiredPersistentKeysRejectsBlankDirectory() {
    IllegalStateException error =
        assertThrows(IllegalStateException.class, () -> config.signingKeys(" ", true));
    assertTrue(error.getMessage().contains("jwt-keys-dir"), "错误信息必须指出缺失配置");
  }

  @Test
  void devFallbackAllowedWhenNotRequired() {
    assertNotNull(config.signingKeys("", false).active(), "开发默认必须允许临时密钥");
  }
}
