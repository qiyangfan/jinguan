package com.longzhong.jinguan;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/** 模块化门禁:跨模块依赖违反(非 api 契约引用/循环)即测试红——架构纪律的 CI 载体。 */
class ModulithVerificationTest {

  @Test
  void verifiesModularStructure() {
    ApplicationModules modules = ApplicationModules.of(JinguanApplication.class);
    modules.verify();
    new Documenter(modules).writeDocumentation(); // 模块依赖图随手刷新(构建产物)
  }
}
