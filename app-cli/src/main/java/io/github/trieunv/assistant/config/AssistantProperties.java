package io.github.trieunv.assistant.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
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
  @Valid private Guard guard = new Guard();
  @NotNull private Path knowledgeDir;
  @NotNull private Path traceDir;

  /** Local run state, such as the previous run's Entry ids for the startup id report. */
  @NotNull private Path stateDir;

  @Getter
  @Setter
  public static class Model {

    @NotNull private ModelProvider provider;

    /** Blank means the provider's default model. */
    private String id;

    @NotNull private Duration timeout;
    @Positive private int maxOutputTokens;

    /** Blank means the model's list price when known, otherwise no cost is recorded. */
    @PositiveOrZero private Double priceInUsdPerMtok;

    @PositiveOrZero private Double priceOutUsdPerMtok;

    @Valid private Provider anthropic = new Provider();
    @Valid private Provider deepseek = new Provider();

    @AssertTrue(message = "MODEL_ID must belong to MODEL_PROVIDER; leave it empty for its default")
    public boolean isModelIdOfProvider() {
      return Objects.isNull(provider) || provider.ownsModelId(resolvedId());
    }

    public String resolvedId() {
      return Optional.ofNullable(id)
          .filter(value -> !value.isBlank())
          .orElse(provider.defaultModelId());
    }
  }

  /** One provider's credentials and endpoint. */
  @Getter
  @Setter
  public static class Provider {

    private String apiKey;

    /** Blank means the provider's own endpoint; set only to point at a local stub. */
    private String baseUrl;
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
  public static class Guard {

    /** Above this share of dropped claims the answer is withheld as unverified. */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double maxDropRatio;
  }

  @Getter
  @Setter
  public static class Prompt {

    @NotBlank private String version;
    @NotNull private Path dir;
  }
}
