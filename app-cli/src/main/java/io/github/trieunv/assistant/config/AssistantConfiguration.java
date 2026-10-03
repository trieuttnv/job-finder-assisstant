package io.github.trieunv.assistant.config;

import io.github.trieunv.assistant.adapter.out.anthropic.AnthropicModelAdapter;
import io.github.trieunv.assistant.adapter.out.lucene.LuceneRetrieverAdapter;
import io.github.trieunv.assistant.adapter.out.state.JsonKnowledgeStateAdapter;
import io.github.trieunv.assistant.adapter.out.trace.JsonlTraceSinkAdapter;
import io.github.trieunv.assistant.adapter.out.yaml.YamlKnowledgeBasePersistenceAdapter;
import io.github.trieunv.assistant.application.dto.AskConfig;
import io.github.trieunv.assistant.application.dto.LoadedKnowledge;
import io.github.trieunv.assistant.application.port.in.AskQuestionUseCase;
import io.github.trieunv.assistant.application.port.in.LoadKnowledgeUseCase;
import io.github.trieunv.assistant.application.port.out.ModelPort;
import io.github.trieunv.assistant.application.service.AskQuestionService;
import io.github.trieunv.assistant.application.service.LoadKnowledgeService;
import io.github.trieunv.assistant.domain.answer.OutputGuardrail;
import io.github.trieunv.assistant.domain.answer.SystemPrompt;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AssistantConfiguration {

  @Bean
  LoadKnowledgeUseCase loadKnowledgeUseCase(AssistantProperties properties) {
    return new LoadKnowledgeService(
        new YamlKnowledgeBasePersistenceAdapter(properties.getKnowledgeDir()),
        new JsonKnowledgeStateAdapter(properties.getStateDir()));
  }

  /** Startup stops here, via {@link KnowledgeFailureAnalyzer}, if any ingest check fails. */
  @Bean
  LoadedKnowledge loadedKnowledge(LoadKnowledgeUseCase loadKnowledge) {
    return loadKnowledge.load();
  }

  @Bean
  AskQuestionUseCase askQuestionUseCase(
      AssistantProperties properties, ModelPort model, LoadedKnowledge knowledge) {
    var config = askConfig(properties);
    return new AskQuestionService(
        new LuceneRetrieverAdapter(knowledge.getEntries(), properties.getRetrieval().getTopK()),
        model,
        new JsonlTraceSinkAdapter(properties.getTraceDir()),
        systemPrompt(properties.getPrompt()),
        config,
        Clock.systemUTC(),
        new OutputGuardrail(
            knowledge.getEntries(), knowledge.getKnownNames(), config.getGuardMaxDropRatio()));
  }

  @Bean
  ModelPort modelPort(AssistantProperties properties) {
    var model = properties.getModel();
    return switch (model.getProvider()) {
      case ANTHROPIC ->
          new AnthropicModelAdapter(
              model.getApiKey(),
              model.getBaseUrl(),
              model.getId(),
              model.getTimeout(),
              model.getMaxOutputTokens());
    };
  }

  private static AskConfig askConfig(AssistantProperties properties) {
    var model = properties.getModel();
    return AskConfig.builder()
        .provider(model.getProvider().name().toLowerCase())
        .modelId(model.getId())
        .timeoutMs(model.getTimeout().toMillis())
        .maxOutputTokens(model.getMaxOutputTokens())
        .retrievalTopK(properties.getRetrieval().getTopK())
        .retrievalMinScore(properties.getRetrieval().getMinScore())
        .guardMaxDropRatio(properties.getGuard().getMaxDropRatio())
        .promptVersion(properties.getPrompt().getVersion())
        .build();
  }

  private static SystemPrompt systemPrompt(AssistantProperties.Prompt prompt) {
    var file = prompt.getDir().resolve(prompt.getVersion() + ".md");
    try {
      return new SystemPrompt(prompt.getVersion(), Files.readString(file));
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read system prompt " + file, e);
    }
  }
}
