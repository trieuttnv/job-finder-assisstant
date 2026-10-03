package io.github.trieunv.assistant.adapter.out.yaml;

import io.github.trieunv.assistant.application.exception.AssistantApplicationException;
import io.github.trieunv.assistant.application.exception.ErrorCode;
import io.github.trieunv.assistant.application.port.out.KnowledgeBasePort;
import io.github.trieunv.assistant.domain.knowledge.Conflict;
import io.github.trieunv.assistant.domain.knowledge.EntryDraft;
import io.github.trieunv.assistant.domain.knowledge.EntryId;
import io.github.trieunv.assistant.domain.knowledge.InvalidKnowledgeException;
import io.github.trieunv.assistant.domain.knowledge.KnowledgeProblem;
import io.github.trieunv.assistant.domain.knowledge.NameList;
import io.github.trieunv.assistant.domain.knowledge.Snapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Reads {@code sources.yaml}, each Source's Snapshot text, every {@code entries/*.yaml} file in
 * file-name order, and the {@code names.yaml} name list. Entries are read as plain maps so an
 * unknown or misspelt field reaches the schema check with the Entry's id.
 */
public class YamlKnowledgeBasePersistenceAdapter implements KnowledgeBasePort {

  private static final TypeReference<List<SourceYaml>> SOURCE_LIST = new TypeReference<>() {};
  private static final TypeReference<List<Map<String, Object>>> ENTRY_LIST =
      new TypeReference<>() {};
  private static final TypeReference<List<ConflictYaml>> CONFLICT_LIST = new TypeReference<>() {};
  private static final Set<String> ENTRY_FIELDS =
      Set.of("id", "type", "source", "parent", "employer", "title", "start", "end", "quote");

  private final Path knowledgeDir;
  private final YAMLMapper yamlMapper =
      YAMLMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  public YamlKnowledgeBasePersistenceAdapter(Path knowledgeDir) {
    this.knowledgeDir = knowledgeDir;
  }

  @Override
  public List<Snapshot> loadSnapshots() {
    var sourcesFile = knowledgeDir.resolve("sources.yaml");
    try {
      return yamlMapper.readValue(sourcesFile.toFile(), SOURCE_LIST).stream()
          .map(this::toSnapshot)
          .toList();
    } catch (JacksonException e) {
      throw invalid("Cannot read Source records in " + sourcesFile, e);
    }
  }

  @Override
  public List<EntryDraft> loadEntryDrafts() {
    var entriesDir = knowledgeDir.resolve("entries");
    try (var files = Files.list(entriesDir)) {
      return files
          .filter(file -> file.getFileName().toString().endsWith(".yaml"))
          .sorted()
          .flatMap(file -> readDrafts(file).stream())
          .toList();
    } catch (IOException e) {
      throw invalid("Cannot list Entry files in " + entriesDir, e);
    }
  }

  @Override
  public NameList loadNameList() {
    var namesFile = knowledgeDir.resolve("names.yaml");
    try {
      var yaml = yamlMapper.readValue(namesFile.toFile(), NameListYaml.class);
      return new NameList(yaml.getTechnologies(), yaml.getAliases());
    } catch (JacksonException e) {
      throw invalid("Cannot read the name list in " + namesFile, e);
    }
  }

  @Override
  public List<Conflict> loadConflicts() {
    var conflictsFile = knowledgeDir.resolve("conflicts.yaml");
    if (!Files.exists(conflictsFile)) {
      return List.of();
    }
    try {
      return yamlMapper.readValue(conflictsFile.toFile(), CONFLICT_LIST).stream()
          .map(YamlKnowledgeBasePersistenceAdapter::toConflict)
          .toList();
    } catch (JacksonException e) {
      throw invalid("Cannot read the Conflicts in " + conflictsFile, e);
    }
  }

  private static Conflict toConflict(ConflictYaml yaml) {
    var incomplete =
        isBlank(yaml.getId())
            || isBlank(yaml.getTopic())
            || yaml.getTerms().isEmpty()
            || isBlank(yaml.getRule())
            || yaml.getSides().stream().anyMatch(side -> isBlank(side.getText()));
    if (incomplete) {
      throw InvalidKnowledgeException.ofConflict(
          Objects.requireNonNullElse(yaml.getId(), "(no id)"),
          KnowledgeProblem.INVALID_SCHEMA,
          "a Conflict needs id, topic, terms, rule and sides with text");
    }
    return new Conflict(
        yaml.getId(),
        yaml.getTopic(),
        yaml.getTerms(),
        yaml.getSides().stream()
            .map(
                side ->
                    new Conflict.ConflictSide(
                        side.getText(), side.getEntryIds().stream().map(EntryId::of).toList()))
            .toList(),
        yaml.getNote(),
        yaml.getRule());
  }

  private Snapshot toSnapshot(SourceYaml source) {
    var id = Objects.requireNonNullElse(source.getId(), "(no id)");
    if (isBlank(source.getId())
        || isBlank(source.getKind())
        || isBlank(source.getSnapshot())
        || isBlank(source.getCollected())
        || isBlank(source.getCopied())
        || Objects.isNull(source.getRedactions())) {
      throw InvalidKnowledgeException.ofSource(
          id,
          KnowledgeProblem.INVALID_SCHEMA,
          "a Source record needs id, kind, snapshot, collected, copied and redactions");
    }
    var path = knowledgeDir.resolve(source.getSnapshot());
    try {
      return new Snapshot(id, knowledgeDir.relativize(path).toString(), snapshotTexts(path));
    } catch (IOException e) {
      throw invalid("Cannot read the Snapshot of Source " + id + " at " + path, e);
    }
  }

  /** A file's text, or for a folder (a repository Snapshot) each file's text in path order. */
  private static List<String> snapshotTexts(Path path) throws IOException {
    if (!Files.isDirectory(path)) {
      return List.of(Files.readString(path));
    }
    try (var files = Files.walk(path)) {
      var texts = new ArrayList<String>();
      for (var file : files.filter(Files::isRegularFile).sorted().toList()) {
        texts.add(Files.readString(file));
      }
      return texts;
    }
  }

  private List<EntryDraft> readDrafts(Path file) {
    try {
      var maps = yamlMapper.readValue(file.toFile(), ENTRY_LIST);
      var fileName = file.getFileName().toString();
      return IntStream.range(0, maps.size())
          .mapToObj(index -> toDraft(fileName + " entry " + (index + 1), maps.get(index)))
          .toList();
    } catch (JacksonException e) {
      throw invalid("Cannot read Entry file " + file, e);
    }
  }

  private static EntryDraft toDraft(String position, Map<String, Object> fields) {
    var unknown = new HashSet<>(fields.keySet());
    unknown.removeAll(ENTRY_FIELDS);
    return new EntryDraft(
        position,
        text(fields, "id"),
        text(fields, "type"),
        text(fields, "source"),
        text(fields, "parent"),
        text(fields, "employer"),
        text(fields, "title"),
        text(fields, "start"),
        text(fields, "end"),
        text(fields, "quote"),
        Set.copyOf(unknown));
  }

  /** A scalar as text; YAML may read {@code 2026} as a number. A list or map is not text. */
  private static String text(Map<String, Object> fields, String name) {
    var value = fields.get(name);
    if (value instanceof List<?> || value instanceof Map<?, ?>) {
      return "(not text)";
    }
    return Objects.isNull(value) ? null : value.toString();
  }

  private static boolean isBlank(String value) {
    return Objects.isNull(value) || value.isBlank();
  }

  private static AssistantApplicationException invalid(String message, Exception cause) {
    return new AssistantApplicationException(ErrorCode.KNOWLEDGE_BASE_INVALID, message, cause);
  }
}
