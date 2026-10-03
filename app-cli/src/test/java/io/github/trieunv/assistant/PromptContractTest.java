package io.github.trieunv.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** Every versioned system prompt works with both providers (ADR 0005). */
class PromptContractTest {

  private static final Path PROMPTS = Path.of("../prompts");

  @Test
  void shouldMentionJsonAndTheClaimsShape_whenAnyPromptVersionIsCommitted() throws IOException {
    try (var files = Files.list(PROMPTS)) {
      var prompts = files.filter(file -> file.toString().endsWith(".md")).toList();

      assertThat(prompts).isNotEmpty();
      for (var prompt : prompts) {
        var text = Files.readString(prompt);
        // DeepSeek's JSON mode rejects a request whose prompt never says "json".
        assertThat(text.toLowerCase(Locale.ROOT)).as(prompt.toString()).contains("json");
        assertThat(text).as(prompt.toString()).contains("\"claims\"").contains("\"notInSources\"");
      }
    }
  }
}
