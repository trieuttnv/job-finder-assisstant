package io.github.trieunv.assistant.domain.knowledge;

import io.github.trieunv.assistant.domain.WholeWords;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The employer, technology and Entry-type names a question can be narrowed by, built at startup
 * from the Entries (README Task 1, Step 5): employers from the employer field, technologies from
 * the committed name list but only those some Entry mentions, and types from the fixed type list.
 */
public final class RetrievalVocabulary {

  private static final Pattern WORD = Pattern.compile("\\p{L}+");

  /**
   * Words that only frame a request. A question made of these and a type word ("Tell me about your
   * skills") asks for the type itself; any other word ("Messi") must match an Entry.
   */
  private static final Set<String> REQUEST_WORDS =
      Set.of(
          "what", "which", "who", "where", "when", "how", "why", "tell", "me", "about", "your",
          "yours", "you", "my", "i", "do", "does", "did", "have", "has", "had", "are", "is", "was",
          "were", "be", "been", "a", "an", "the", "of", "in", "on", "for", "with", "and", "or",
          "any", "all", "some", "list", "show", "give", "describe", "explain", "talk", "share",
          "please", "can", "could", "would", "will", "us", "more", "there", "to", "from", "past");

  private final Map<Pattern, String> employerByPattern;
  private final Map<Pattern, String> technologyByPattern;
  private final Map<Pattern, EntryType> typeByPattern;
  private final Map<Pattern, String> aliasToCanonical;

  private RetrievalVocabulary(
      Map<Pattern, String> employers,
      Map<Pattern, String> technologies,
      Map<Pattern, EntryType> types,
      Map<Pattern, String> aliases) {
    this.employerByPattern = employers;
    this.technologyByPattern = technologies;
    this.typeByPattern = types;
    this.aliasToCanonical = aliases;
  }

  public static RetrievalVocabulary from(List<Entry> entries, NameList nameList) {
    // Interviewers type employers in any case ("at tamara").
    var employers = new LinkedHashMap<Pattern, String>();
    entries.stream()
        .filter(entry -> Objects.nonNull(entry.employer()))
        .forEach(
            entry ->
                entry
                    .employerNames()
                    .forEach(
                        name ->
                            employers.put(
                                WholeWords.pattern(name, Pattern.CASE_INSENSITIVE),
                                entry.employer())));

    // Technologies only as written, as in names.yaml: "go" and "visa" are plain words.
    var entryText = String.join("\n", entries.stream().map(Entry::quote).toList());
    var technologies = new LinkedHashMap<Pattern, String>();
    nameList.technologies().stream()
        .filter(name -> WholeWords.pattern(name, 0).matcher(entryText).find())
        .forEach(name -> technologies.put(WholeWords.pattern(name, 0), name));

    var types = new LinkedHashMap<Pattern, EntryType>();
    for (var type : EntryType.values()) {
      types.put(typeWordPattern(type), type);
    }
    return new RetrievalVocabulary(employers, technologies, types, nameList.aliasPatterns());
  }

  public QuestionScope scopeOf(String question) {
    var text = question;
    for (var alias : aliasToCanonical.entrySet()) {
      text = alias.getKey().matcher(text).replaceAll(alias.getValue());
    }
    var types = found(typeByPattern, text);
    return new QuestionScope(
        found(employerByPattern, text),
        found(technologyByPattern, text),
        types,
        !types.isEmpty() && onlyRequestAndTypeWords(text));
  }

  /** The employers {@code text} names, such as a summary Entry's Quote that mentions two. */
  public Set<String> employersNamedIn(String text) {
    return found(employerByPattern, text);
  }

  private boolean onlyRequestAndTypeWords(String text) {
    return WORD.matcher(text)
        .results()
        .map(match -> match.group())
        .filter(word -> !REQUEST_WORDS.contains(word.toLowerCase(Locale.ROOT)))
        .allMatch(word -> typeByPattern.keySet().stream().anyMatch(p -> p.matcher(word).matches()));
  }

  /** The type code's first word, singular or plural: "skill", "skills", "summaries". */
  private static Pattern typeWordPattern(EntryType type) {
    var word = type.code().split("-")[0];
    var plural = word.endsWith("y") ? word.substring(0, word.length() - 1) + "ies" : word + "s";
    return Pattern.compile(
        "(?<![\\p{L}])(" + word + "|" + plural + ")(?![\\p{L}])", Pattern.CASE_INSENSITIVE);
  }

  private static <T> Set<T> found(Map<Pattern, T> byPattern, String text) {
    var found = new LinkedHashSet<T>();
    byPattern.forEach(
        (pattern, value) -> {
          if (pattern.matcher(text).find()) {
            found.add(value);
          }
        });
    return found;
  }
}
