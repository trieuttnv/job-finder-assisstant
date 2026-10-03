package io.github.trieunv.assistant.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.nio.file.Path;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Every setting comes from the environment; {@code application.yml} maps the variable names. */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "assistant")
public class AssistantProperties {

  @Valid private Model model = new Model();
  @Valid private Retrieval retrieval = new Retrieval();
  @Valid private Prompt prompt = new Prompt();
  @NotNull private Path knowledgeDir;
  @NotNull private Path traceDir;

  /** Local run state, such as the previous run's Entry ids for the startup id report. */
  @NotNull private Path stateDir;

  @Getter
  @Setter
  public static class Model {

    @NotNull private ModelProvider provider;
    @NotBlank private String id;
    private String apiKey;

    /** Blank means Anthropic's default endpoint. */
    private String baseUrl;

    @NotNull private Duration timeout;
    @Positive private int maxOutputTokens;
  }

  @Getter
  @Setter
  public static class Retrieval {

    @Positive private int topK;

    /** The Input Guardrail floor on the best BM25 score; set on the dev split. */
    @PositiveOrZero private double minScore;
  }

  @Getter
  @Setter
  public static class Prompt {

    @NotBlank private String version;
    @NotNull private Path dir;
  }
}
