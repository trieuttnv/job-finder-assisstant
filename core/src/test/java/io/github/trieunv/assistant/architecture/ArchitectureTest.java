package io.github.trieunv.assistant.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "io.github.trieunv.assistant",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  /** The core stays framework-free, so the CLI, the eval and a future HTTP service share it. */
  @ArchTest
  static final ArchRule coreImportsNoFramework =
      noClasses()
          .that()
          .resideInAnyPackage("..domain..", "..application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "org.apache.lucene..",
              "com.fasterxml.jackson..",
              "tools.jackson..");

  @ArchTest
  static final ArchRule domainDependsOnNothingElse =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..application..", "..adapter..");

  @ArchTest
  static final ArchRule applicationNeverUsesAdapters =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..adapter..");
}
