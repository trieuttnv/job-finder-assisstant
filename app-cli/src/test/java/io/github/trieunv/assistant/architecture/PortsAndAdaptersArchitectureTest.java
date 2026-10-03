package io.github.trieunv.assistant.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The Ports and Adapters dependency rules across core, adapters-out, wiring and the CLI's driving
 * adapter, which are all on app-cli's classpath. app-eval's driving adapter follows the same rules
 * by review, not by this test.
 */
@AnalyzeClasses(
    packages = "io.github.trieunv.assistant",
    importOptions = ImportOption.DoNotIncludeTests.class)
class PortsAndAdaptersArchitectureTest {

  @ArchTest
  static final ArchRule applicationNeverUsesAdapters =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..adapter..");

  /** A driving adapter calls use-case interfaces only. */
  @ArchTest
  static final ArchRule drivingAdaptersCallUseCasesOnly =
      noClasses()
          .that()
          .resideInAPackage("..adapter.in..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..application.service..", "..application.port.out..", "..adapter.out..");

  @ArchTest
  static final ArchRule drivenAdaptersNeverUseEachOther =
      slices().matching("..adapter.out.(*)..").should().notDependOnEachOther();

  /** Only the wiring knows which adapter implements which port. */
  @ArchTest
  static final ArchRule onlyWiringReferencesDrivenAdapters =
      noClasses()
          .that()
          .resideOutsideOfPackages("..adapter.out..", "..config..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..adapter.out..");
}
