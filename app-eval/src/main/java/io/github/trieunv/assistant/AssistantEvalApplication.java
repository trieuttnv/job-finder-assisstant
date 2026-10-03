package io.github.trieunv.assistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** The eval runner's entry point. The runner and scorer land in ticket 10. */
@SpringBootApplication
public class AssistantEvalApplication {

  public static void main(String[] args) {
    SpringApplication.run(AssistantEvalApplication.class, args);
  }
}
