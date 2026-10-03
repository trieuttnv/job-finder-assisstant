package io.github.trieunv.assistant.config;

import io.github.trieunv.assistant.application.dto.TokenPrice;
import java.util.Map;
import java.util.Optional;

/**
 * Which {@code ModelPort} adapter answers; set by {@code MODEL_PROVIDER}. Each provider has the
 * model it uses when {@code MODEL_ID} is unset, and the prefix every one of its model ids has
 * (ADR 0005).
 */
public enum ModelProvider {
  ANTHROPIC("claude-sonnet-5-5", "claude-"),
  DEEPSEEK("deepseek-flash", "deepseek-");

  /** List prices this build knows; any other model records no cost unless MODEL_PRICE_* is set. */
  private static final Map<String, TokenPrice> LIST_PRICES =
      Map.of("claude-sonnet-5-5", new TokenPrice(2.0, 10.0));

  private final String defaultModelId;
  private final String modelIdPrefix;

  ModelProvider(String defaultModelId, String modelIdPrefix) {
    this.defaultModelId = defaultModelId;
    this.modelIdPrefix = modelIdPrefix;
  }

  public String defaultModelId() {
    return defaultModelId;
  }

  /** Catches a leftover MODEL_ID of the other provider, which that provider would reject. */
  public boolean ownsModelId(String modelId) {
    return modelId.startsWith(modelIdPrefix);
  }

  static Optional<TokenPrice> listPrice(String modelId) {
    return Optional.ofNullable(LIST_PRICES.get(modelId));
  }
}
