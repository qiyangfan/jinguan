package com.longzhong.jinguan;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import org.junit.jupiter.api.Test;

/**
 * 字节码级架构约束:common 不得反向依赖业务域、控制器不得直连仓储、业务域包切片无环;只导入生产类。
 *
 * <p>{@code JavaClasses} 是 ArchUnit 用来承载导入结果的实现类型(无对应接口),此处分层规则不适用。
 */
@SuppressWarnings("PMD.LooseCoupling")
class ArchitectureTest {

  private static final String ROOT = "com.longzhong.jinguan";

  @Test
  void commonDoesNotDependOnBusinessDomains() {
    noClasses()
        .that()
        .resideInAPackage(ROOT + ".common..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage(ROOT + ".(security|topology|payment|ingest|user|wallet|pricing)..")
        .because("common 是被各域共用的底座,反向依赖业务域会造成循环")
        .check(productionClasses());
  }

  @Test
  void controllersDoNotDependOnRepositories() {
    noClasses()
        .that()
        .haveSimpleNameEndingWith("Controller")
        .should()
        .dependOnClassesThat()
        .haveSimpleNameEndingWith("Repository")
        .because("控制器经 service 访问持久化,绕开 service 会跳过事务与业务规则")
        .check(productionClasses());
  }

  @Test
  void businessDomainSlicesHaveNoCycles() {
    SlicesRuleDefinition.slices()
        .matching(ROOT + ".(*)..")
        .should()
        .beFreeOfCycles()
        .check(productionClasses());
  }

  /** 导入被测生产类,排除测试夹具。 */
  private static JavaClasses productionClasses() {
    return new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages(ROOT);
  }
}
