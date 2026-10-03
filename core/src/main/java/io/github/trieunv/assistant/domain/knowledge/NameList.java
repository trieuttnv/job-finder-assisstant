package io.github.trieunv.assistant.domain.knowledge;

import io.github.trieunv.assistant.domain.WholeWords;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The committed list of technology and product names, and known aliases (canonical name to its
 * other spellings, such as Go to Golang). It adds names no Entry states, so a claim naming one of
 * them can be checked too.
 */
public record NameList(List<String> technologies, Map<String, List<String>> aliases) {

  public NameList {
    technologies = List.copyOf(technologies);
    aliases = Map.copyOf(aliases);
  }

  public static NameList empty() {
    return new NameList(List.of(), Map.of());
  }

  /** Each alias spelling, matched as a whole word in any case, to its canonical name. */
  public Map<Pattern, String> aliasPatterns() {
    var patterns = new LinkedHashMap<Pattern, String>();
    aliases.forEach(
        (canonical, spellings) ->
            spellings.forEach(
                spelling ->
                    patterns.put(
                        WholeWords.pattern(spelling, Pattern.CASE_INSENSITIVE), canonical)));
    return patterns;
  }
}
