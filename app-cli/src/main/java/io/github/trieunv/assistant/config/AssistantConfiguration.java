package io.github.trieunv.assistant.config;

import io.github.trieunv.assistant.adapter.out.anthropic.AnthropicModelAdapter;
import io.github.trieunv.assistant.adapter.out.lucene.LuceneRetrieverAdapter;
import io.github.trieunv.assistant.adapter.out.trace.JsonlTraceSinkAdapter;
import io.github.trieunv.assistant.adapter.out.yaml.YamlKnowledgeBasePersistenceAdapter;
import io.github.trieunv.assistant.application.port.in.AskQuestionUseCase;
import io.github.trieunv.assistant.application.port.out.ModelPort;
import io.github.trieunv.assistant.application.service.AskQuestionService;
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
  AskQuestionUseCase askQuestionUseCase(AssistantProperties properties, ModelPort model) {
    var entries =
        new YamlKnowledgeBasePersistenceAdapter(properties.getKnowledgeDir()).loadEntries();
    return new AskQuestionService(
        new LuceneRetrieverAdapter(entries, properties.getRetrieval().getTopK()),
        model,
        new JsonlTraceSinkAdapter(properties.getTraceDir()),
        systemPrompt(properties.getPrompt()),
        Clock.systemUTC());
  }

  @Bean
  ModelPort modelPort(AssistantProperties properties) {
    var model = properties.getModel();
    if (!"anthropic".equals(model.getProvider())) {
      throw new IllegalStateException(
          "MODEL_PROVIDER=" + model.getProvider() + " is not supported; use anthropic");
    }
    return new AnthropicModelAdapter(
        model.getApiKey(), model.getId(), model.getTimeout(), model.getMaxOutputTokens());
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
