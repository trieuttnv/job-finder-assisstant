package io.github.trieunv.assistant.domain.knowledge;

import io.github.trieunv.assistant.domain.WholeWords;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The employer, product and technology names the Output Guardrail checks claims for, built at
 * startup from every Entry's employer field and the committed {@link NameList}, never hand-coded.
 */
public final class KnownNames {

  /** Each known name with the pattern that finds it in a claim. */
  private final Map<String, Pattern> namePatterns;

  private final Map<Pattern, String> aliasToCanonical;

  private KnownNames(Set<String> names, Map<Pattern, String> aliasToCanonical) {
    this.namePatterns = new LinkedHashMap<>();
    names.forEach(name -> namePatterns.put(name, namePattern(name)));
    this.aliasToCanonical = aliasToCanonical;
  }

  public static KnownNames from(Collection<Entry> entries, NameList nameList) {
    var names = new LinkedHashSet<String>(nameList.technologies());
    entries.stream().flatMap(Entry::employerNames).forEach(names::add);
    names.addAll(nameList.aliases().keySet());
    return new KnownNames(names, nameList.aliasPatterns());
  }

  /** {@code text} with every known alias replaced by its canonical name. */
  public String withCanonicalNames(String text) {
    var result = text;
    for (var alias : aliasToCanonical.entrySet()) {
      result = alias.getKey().matcher(result).replaceAll(alias.getValue());
    }
    return result;
  }

  /** The known names {@code text} mentions, as listed. */
  public Set<String> mentionedIn(String text) {
    var found = new LinkedHashSet<String>();
    namePatterns.forEach(
        (name, pattern) -> {
          if (pattern.matcher(text).find()) {
            found.add(name);
          }
        });
    return found;
  }

  /** As written, so "go", "visa" and "react" as plain words are not Go, Visa and React. */
  private static Pattern namePattern(String name) {
    return WholeWords.pattern(name, 0);
  }
}
