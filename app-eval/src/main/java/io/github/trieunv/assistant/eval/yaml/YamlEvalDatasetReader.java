package io.github.trieunv.assistant.eval.yaml;

import io.github.trieunv.assistant.eval.EvalDataset;
import io.github.trieunv.assistant.eval.EvalItem;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.dataformat.yaml.YAMLMapper;

/** Reads {@code evals/dataset.yaml}; an unknown or misspelt field fails the read. */
public class YamlEvalDatasetReader {

  private static final TypeReference<List<ItemYaml>> ITEMS = new TypeReference<>() {};

  private final YAMLMapper yaml =
      YAMLMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  public EvalDataset read(Path file) {
    return new EvalDataset(
        yaml.readValue(file.toFile(), ITEMS).stream().map(YamlEvalDatasetReader::toItem).toList());
  }

  private static EvalItem toItem(ItemYaml item) {
    var gold = Objects.requireNonNullElseGet(item.getGold(), GoldYaml::new);
    return new EvalItem(
        item.getId(),
        toEnum(EvalItem.Split.class, "split", item),
        toEnum(EvalItem.Category.class, "category", item),
        Optional.ofNullable(item.getKind())
            .map(kind -> EvalItem.AdversarialKind.valueOf(enumName(kind)))
            .orElse(null),
        item.getQuestion(),
        toEnum(EvalItem.Expectation.class, "expect", item),
        new EvalItem.GoldLabel(gold.getEntryIds(), gold.getMustContain(), gold.getMustNotClaim()),
        item.getRewordOf(),
        item.isVerified());
  }

  private static <E extends Enum<E>> E toEnum(Class<E> type, String field, ItemYaml item) {
    var value =
        switch (field) {
          case "split" -> item.getSplit();
          case "category" -> item.getCategory();
          default -> item.getExpect();
        };
    if (Objects.isNull(value)) {
      throw new IllegalArgumentException("Eval item " + item.getId() + " has no " + field);
    }
    return Enum.valueOf(type, enumName(value));
  }

  /** The dataset writes values the way people read them: "multi-source", "out-of-scope". */
  private static String enumName(String value) {
    return value.strip().replace('-', '_').toUpperCase(Locale.ROOT);
  }

  @Getter
  @Setter
  @NoArgsConstructor
  static class ItemYaml {

    private String id;
    private String split;
    private String category;
    private String kind;
    private String question;
    private String expect;
    private GoldYaml gold;
    private String rewordOf;
    private boolean verified;
  }

  @Getter
  @Setter
  @NoArgsConstructor
  static class GoldYaml {

    private List<String> entryIds = List.of();
    private List<List<String>> mustContain = List.of();
    private List<String> mustNotClaim = List.of();
  }
}
