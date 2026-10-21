package com.longzhong.jinguan;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/** 验证模块间依赖遵守契约且无循环。 */
class ModulithVerificationTest {

  @Test
  void verifiesModularStructure() {
    ApplicationModules modules = ApplicationModules.of(JinguanApplication.class);
    modules.verify();
    new Documenter(modules).writeDocumentation(); // 顺带刷新模块依赖图（构建产物）
  }
}
