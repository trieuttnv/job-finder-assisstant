package io.github.trieunv.assistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AssistantCliApplication {

  public static void main(String[] args) {
    SpringApplication.run(AssistantCliApplication.class, args);
  }
}
