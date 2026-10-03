package io.github.trieunv.assistant.adapter.out.yaml;

import io.github.trieunv.assistant.application.exception.AssistantApplicationException;
import io.github.trieunv.assistant.application.exception.ErrorCode;
import io.github.trieunv.assistant.application.port.out.KnowledgeBasePort;
import io.github.trieunv.assistant.domain.exception.AssistantDomainException;
import io.github.trieunv.assistant.domain.knowledge.Entry;
import io.github.trieunv.assistant.domain.knowledge.EntryId;
import io.github.trieunv.assistant.domain.knowledge.EntryType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.dataformat.yaml.YAMLMapper;

/** Reads every {@code entries/*.yaml} file under the knowledge folder, in file-name order. */
public class YamlKnowledgeBasePersistenceAdapter implements KnowledgeBasePort {

  private static final TypeReference<List<EntryYaml>> ENTRY_LIST = new TypeReference<>() {};

  private final Path entriesDir;
  private final YAMLMapper yamlMapper = YAMLMapper.builder().build();

  public YamlKnowledgeBasePersistenceAdapter(Path knowledgeDir) {
    this.entriesDir = knowledgeDir.resolve("entries");
  }

  @Override
  public List<Entry> loadEntries() {
    try (var files = Files.list(entriesDir)) {
      return files
          .filter(file -> file.getFileName().toString().endsWith(".yaml"))
          .sorted()
          .flatMap(file -> yamlMapper.readValue(file.toFile(), ENTRY_LIST).stream())
          .map(YamlKnowledgeBasePersistenceAdapter::toEntry)
          .toList();
    } catch (IOException | JacksonException | AssistantDomainException e) {
      throw new AssistantApplicationException(
          ErrorCode.KNOWLEDGE_BASE_INVALID, "Cannot load Entry files in " + entriesDir, e);
    }
  }

  private static Entry toEntry(EntryYaml yaml) {
    return new Entry(
        EntryId.of(yaml.getId()),
        EntryType.fromCode(yaml.getType()),
        yaml.getSource(),
        Optional.ofNullable(yaml.getParent()).map(EntryId::of).orElse(null),
        yaml.getEmployer(),
        yaml.getTitle(),
        yaml.getStart(),
        yaml.getEnd(),
        yaml.getQuote());
  }
}
