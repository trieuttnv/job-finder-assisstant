package io.github.trieunv.assistant.domain.answer;

import io.github.trieunv.assistant.domain.WholeWords;
import io.github.trieunv.assistant.domain.knowledge.Entry;
import io.github.trieunv.assistant.domain.knowledge.EntryId;
import io.github.trieunv.assistant.domain.knowledge.KnownNames;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Decides, with code and no model, which proposed claims may be shown (ADR 0004). A claim is
 * dropped when it cites no Entry or an Entry not retrieved for this question; when it contains a
 * number, year or month none of its cited Entries contain; or when it names an employer, product
 * or technology none of its cited Entries contain. A name is any known name (from the Entries and
 * the committed list) or any capitalised word that doesn't start a sentence, so an employer no
 * Entry mentions ("Goldman") is caught too. A cited bullet brings its parent role (ADR 0001), so
 * the role's employer and dates support the claim, and nothing from any other role does.
 */
public class OutputGuardrail {

  private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");

  /** Capitalised words that are not names: the Subject and their CV. */
  private static final Set<String> NOT_NAMES = Set.of("I", "CV");

  private final Map<EntryId, Entry> entriesById;
  private final KnownNames knownNames;
  private final double maxDropRatio;

  /**
   * @param entries every Entry in the knowledge base, to find a cited bullet's role
   */
  public OutputGuardrail(List<Entry> entries, KnownNames knownNames, double maxDropRatio) {
    this.entriesById =
        entries.stream().collect(Collectors.toMap(Entry::id, entry -> entry, (a, b) -> a));
    this.knownNames = knownNames;
    this.maxDropRatio = maxDropRatio;
  }

  public GuardrailVerdict check(List<Claim> claims, Map<EntryId, Entry> retrieved) {
    var kept = new ArrayList<Claim>();
    var dropped = new ArrayList<DroppedClaim>();
    for (var claim : claims) {
      dropReason(claim, retrieved)
          .ifPresentOrElse(
              reason -> dropped.add(new DroppedClaim(claim, reason)), () -> kept.add(claim));
    }
    var withheld = kept.isEmpty() || (double) dropped.size() / claims.size() > maxDropRatio;
    return new GuardrailVerdict(kept, dropped, withheld);
  }

  private Optional<DropReason> dropReason(Claim claim, Map<EntryId, Entry> retrieved) {
    if (claim.entryIds().isEmpty()) {
      return Optional.of(DropReason.CITES_NOTHING);
    }
    if (!retrieved.keySet().containsAll(claim.entryIds())) {
      return Optional.of(DropReason.CITES_ENTRY_NOT_RETRIEVED);
    }
    var cited =
        claim.entryIds().stream()
            .map(retrieved::get)
            .flatMap(entry -> Stream.of(entry, parentOf(entry)))
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    var citedText = knownNames.withCanonicalNames(citedText(cited));
    var claimText = knownNames.withCanonicalNames(claim.text());
    if (!numbersAndDatesSupported(claimText, citedText, cited)) {
      return Optional.of(DropReason.NUMBER_NOT_IN_SOURCES);
    }
    if (!namesSupported(claimText, citedText)) {
      return Optional.of(DropReason.NAME_NOT_IN_SOURCES);
    }
    return Optional.empty();
  }

  private Entry parentOf(Entry entry) {
    return Optional.ofNullable(entry.parent()).map(entriesById::get).orElse(null);
  }

  private static boolean numbersAndDatesSupported(
      String claimText, String citedText, List<Entry> cited) {
    var citedNumbers = ClaimText.numbers(citedText);
    var citedMonths = ClaimText.months(citedText);
    cited.stream()
        .flatMap(entry -> Stream.of(entry.start(), entry.end()))
        .filter(Objects::nonNull)
        .map(YearMonth::parse)
        .forEach(
            date -> {
              citedNumbers.add(String.valueOf(date.getYear()));
              citedMonths.add(date.getMonth());
            });
    return citedNumbers.containsAll(ClaimText.numbers(claimText))
        && citedMonths.containsAll(ClaimText.months(claimText));
  }

  private boolean namesSupported(String claimText, String citedText) {
    var citedWords =
        WORD.matcher(citedText.toLowerCase(Locale.ROOT))
            .results()
            .map(match -> match.group())
            .collect(Collectors.toSet());
    var citedLower = citedText.toLowerCase(Locale.ROOT);
    var knownSupported =
        knownNames.mentionedIn(claimText).stream()
            .allMatch(name -> containsPhrase(citedLower, name.toLowerCase(Locale.ROOT)));
    return knownSupported
        && capitalisedWords(claimText).stream()
            .allMatch(word -> citedWords.contains(word.toLowerCase(Locale.ROOT)));
  }

  /** Capitalised words that don't start a sentence, aren't months and aren't numbers. */
  private static List<String> capitalisedWords(String text) {
    var words = new ArrayList<String>();
    var matcher = WORD.matcher(text);
    while (matcher.find()) {
      var word = matcher.group();
      var capitalised = word.chars().anyMatch(Character::isUpperCase);
      if (capitalised
          && !startsSentence(text, matcher.start())
          && !NOT_NAMES.contains(word)
          && !ClaimText.isMonth(word)
          && !ClaimText.NUMBER.matcher(word).matches()) {
        words.add(word);
      }
    }
    return words;
  }

  private static boolean startsSentence(String text, int start) {
    var index = start - 1;
    while (index >= 0 && !Character.isLetterOrDigit(text.charAt(index))) {
      if (".!?:;".indexOf(text.charAt(index)) >= 0) {
        return true;
      }
      index--;
    }
    return index < 0;
  }

  private static boolean containsPhrase(String text, String phrase) {
    return WholeWords.pattern(phrase, 0).matcher(text).find();
  }

  private static String citedText(List<Entry> cited) {
    return cited.stream()
        .flatMap(entry -> Stream.of(entry.quote(), entry.employer(), entry.title()))
        .filter(Objects::nonNull)
        .collect(Collectors.joining(" \n "));
  }
}
