package io.github.trieunv.assistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * The evaluation runner ({@code ./eval.sh}): the same wiring and Ask use case as the CLI, with the
 * evaluation as its only driving adapter. Its exit code is the run's verdict.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AssistantEvalApplication {

  public static void main(String[] args) {
    System.exit(
        SpringApplication.exit(SpringApplication.run(AssistantEvalApplication.class, args)));
  }
}
