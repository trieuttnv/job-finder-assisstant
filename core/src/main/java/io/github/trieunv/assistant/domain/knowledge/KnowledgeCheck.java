package io.github.trieunv.assistant.domain.knowledge;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The ingest checks run at every startup (README Task 1, Step 5): no email left in a Snapshot, a
 * valid schema, unique ids, declared Sources, parents that resolve to a role, Quotes word for word
 * in their Snapshot, and typed fields that appear in their Quote. The first problem stops the load.
 */
public final class KnowledgeCheck {

  private static final Pattern ID =
      Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*(\\.[a-z0-9]+(-[a-z0-9]+)*)+");
  private static final Pattern YEAR_MONTH = Pattern.compile("(\\d{4})-(0[1-9]|1[0-2])");
  private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");

  private KnowledgeCheck() {}

  public static List<Entry> check(List<Snapshot> snapshots, List<EntryDraft> drafts) {
    snapshots.forEach(KnowledgeCheck::requireRedacted);
    var snapshotsBySource = new HashMap<String, Snapshot>();
    for (var snapshot : snapshots) {
      if (Objects.nonNull(snapshotsBySource.putIfAbsent(snapshot.sourceId(), snapshot))) {
        throw InvalidKnowledgeException.ofSource(
            snapshot.sourceId(),
            KnowledgeProblem.DUPLICATE_ID,
            "the id is declared more than once");
      }
    }

    var entries = new LinkedHashMap<EntryId, Entry>();
    for (var draft : drafts) {
      var entry = toEntry(draft);
      if (Objects.nonNull(entries.putIfAbsent(entry.id(), entry))) {
        throw InvalidKnowledgeException.ofEntry(
            entry.id().value(), KnowledgeProblem.DUPLICATE_ID, "the id is used more than once");
      }
    }
    for (var entry : entries.values()) {
      var snapshot = snapshotsBySource.get(entry.source());
      if (Objects.isNull(snapshot)) {
        throw problem(
            entry,
            KnowledgeProblem.UNKNOWN_SOURCE,
            "Source '" + entry.source() + "' is not declared");
      }
      requireParentRole(entry, entries);
      requireQuoteInSnapshot(entry, snapshot);
      requireTypedFieldsInQuote(entry);
    }
    return List.copyOf(entries.values());
  }

  /**
   * Entries whose Quote is word for word in their Snapshot, over all Entries: the evaluation's
   * Quote provenance metric, counted rather than assumed even though {@link #check} already
   * refuses any miss.
   */
  public static double quoteProvenance(List<Snapshot> snapshots, List<Entry> entries) {
    if (entries.isEmpty()) {
      return 1.0;
    }
    var bySource = new HashMap<String, Snapshot>();
    snapshots.forEach(snapshot -> bySource.putIfAbsent(snapshot.sourceId(), snapshot));
    var found =
        entries.stream()
            .filter(
                entry ->
                    Optional.ofNullable(bySource.get(entry.source()))
                        .map(snapshot -> quoteIsIn(entry, snapshot))
                        .orElse(false))
            .count();
    return (double) found / entries.size();
  }

  /** Every Conflict has two or more sides, each citing Entries that exist. */
  public static void checkConflicts(List<Conflict> conflicts, List<Entry> entries) {
    var ids = entries.stream().map(Entry::id).collect(Collectors.toSet());
    for (var conflict : conflicts) {
      if (conflict.sides().size() < 2
          || conflict.sides().stream().anyMatch(side -> side.entryIds().isEmpty())) {
        throw InvalidKnowledgeException.ofConflict(
            conflict.id(),
            KnowledgeProblem.INVALID_SCHEMA,
            "a Conflict needs two or more sides, each citing an Entry");
      }
      conflict.entryIds().stream()
          .filter(id -> !ids.contains(id))
          .findFirst()
          .ifPresent(
              missing -> {
                throw InvalidKnowledgeException.ofConflict(
                    conflict.id(),
                    KnowledgeProblem.UNRESOLVED_CONFLICT_SIDE,
                    "a side cites '" + missing + "', which is not an Entry");
              });
    }
  }

  private static void requireRedacted(Snapshot snapshot) {
    if (snapshot.texts().stream().anyMatch(text -> EMAIL.matcher(text).find())) {
      throw InvalidKnowledgeException.ofSource(
          snapshot.sourceId(),
          KnowledgeProblem.UNREDACTED_EMAIL,
          snapshot.location() + " contains an email address; replace it with [redacted-email]");
    }
  }

  private static Entry toEntry(EntryDraft draft) {
    if (isBlank(draft.id())) {
      throw schema(draft.position(), "the Entry has no id");
    }
    var target = draft.id();
    if (!draft.unknownFields().isEmpty()) {
      throw schema(target, "unknown field(s) " + draft.unknownFields().stream().sorted().toList());
    }
    if (!ID.matcher(draft.id()).matches()) {
      throw schema(
          target, "the id must be lower-case words joined by '.', such as cv.role.tamara");
    }
    var type =
        EntryType.fromCode(draft.type())
            .orElseThrow(
                () -> schema(target, "type '" + draft.type() + "' is not one of " + typeCodes()));
    if (isBlank(draft.source())) {
      throw schema(target, "the Entry has no source");
    }
    if (!draft.id().startsWith(draft.source() + ".")) {
      throw schema(target, "the id must start with its source, '" + draft.source() + ".'");
    }
    if (isBlank(draft.quote())) {
      throw schema(target, "the Entry has no quote");
    }
    requireYearMonth(target, "start", draft.start());
    requireYearMonth(target, "end", draft.end());
    if (type == EntryType.ROLE
        && (isBlank(draft.employer()) || isBlank(draft.title()) || isBlank(draft.start()))) {
      throw schema(target, "a role needs employer, title and start");
    }
    if (type == EntryType.BULLET && isBlank(draft.parent())) {
      throw schema(target, "a bullet needs a parent role");
    }
    if (type != EntryType.BULLET && !isBlank(draft.parent())) {
      throw schema(target, "only a bullet has a parent");
    }
    return new Entry(
        EntryId.of(draft.id()),
        type,
        draft.source(),
        Optional.ofNullable(draft.parent()).map(EntryId::of).orElse(null),
        draft.employer(),
        draft.title(),
        draft.start(),
        draft.end(),
        draft.quote());
  }

  private static void requireYearMonth(String target, String field, String value) {
    if (Objects.nonNull(value) && !YEAR_MONTH.matcher(value).matches()) {
      throw schema(target, field + " '" + value + "' must be year and month, such as 2022-10");
    }
  }

  private static void requireParentRole(Entry entry, Map<EntryId, Entry> entries) {
    if (Objects.isNull(entry.parent())) {
      return;
    }
    var parent = entries.get(entry.parent());
    if (Objects.isNull(parent) || parent.type() != EntryType.ROLE) {
      throw problem(
          entry,
          KnowledgeProblem.UNRESOLVED_PARENT,
          "parent '" + entry.parent() + "' is not a role Entry");
    }
  }

  private static boolean quoteIsIn(Entry entry, Snapshot snapshot) {
    var quoteKey = QuoteText.key(entry.quote());
    return snapshot.texts().stream()
        .anyMatch(text -> QuoteText.containsWords(QuoteText.key(text), quoteKey));
  }

  private static void requireQuoteInSnapshot(Entry entry, Snapshot snapshot) {
    if (!quoteIsIn(entry, snapshot)) {
      throw problem(
          entry,
          KnowledgeProblem.QUOTE_NOT_IN_SNAPSHOT,
          "the quote is not word for word in " + snapshot.location());
    }
  }

  private static void requireTypedFieldsInQuote(Entry entry) {
    var quote = QuoteText.key(entry.quote());
    requireText(entry, "employer", entry.employer(), quote);
    requireText(entry, "title", entry.title(), quote);
    requireDate(entry, "start", entry.start(), quote);
    requireDate(entry, "end", entry.end(), quote);
  }

  private static void requireText(Entry entry, String field, String value, String quote) {
    if (Objects.nonNull(value) && !QuoteText.containsWords(quote, QuoteText.key(value))) {
      throw typedFieldMissing(entry, field, value);
    }
  }

  /** {@code 2017-06} appears as {@code Jun 2017} or {@code June 2017}. */
  private static void requireDate(Entry entry, String field, String value, String quote) {
    if (Objects.isNull(value)) {
      return;
    }
    var matcher = YEAR_MONTH.matcher(value);
    if (!matcher.matches()) {
      throw typedFieldMissing(entry, field, value);
    }
    var month = Month.of(Integer.parseInt(matcher.group(2)));
    var year = matcher.group(1);
    var written =
        List.of(TextStyle.SHORT, TextStyle.FULL).stream()
            .map(style -> month.getDisplayName(style, Locale.ENGLISH) + " " + year)
            .anyMatch(date -> QuoteText.containsWords(quote, date));
    if (!written) {
      throw typedFieldMissing(entry, field, value);
    }
  }

  private static InvalidKnowledgeException typedFieldMissing(
      Entry entry, String field, String value) {
    return problem(
        entry,
        KnowledgeProblem.TYPED_FIELD_NOT_IN_QUOTE,
        field + " '" + value + "' is not in the quote");
  }

  private static InvalidKnowledgeException problem(
      Entry entry, KnowledgeProblem problem, String reason) {
    return InvalidKnowledgeException.ofEntry(entry.id().value(), problem, reason);
  }

  private static InvalidKnowledgeException schema(String target, String reason) {
    return InvalidKnowledgeException.ofEntry(target, KnowledgeProblem.INVALID_SCHEMA, reason);
  }

  private static String typeCodes() {
    return Arrays.stream(EntryType.values()).map(EntryType::code).toList().toString();
  }

  private static boolean isBlank(String value) {
    return Objects.isNull(value) || value.isBlank();
  }
}
