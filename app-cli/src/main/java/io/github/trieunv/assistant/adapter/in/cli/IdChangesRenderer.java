package io.github.trieunv.assistant.adapter.in.cli;

import io.github.trieunv.assistant.application.dto.LoadedKnowledge;
import java.util.List;

/** The startup id report: one line of counts, then the ids under each non-empty heading. */
public class IdChangesRenderer {

  public String render(LoadedKnowledge knowledge) {
    var changes = knowledge.getChanges();
    var out = new StringBuilder();
    out.append("Knowledge base: ")
        .append(knowledge.getEntries().size())
        .append(" Entries. Since the last run: ")
        .append(changes.added().size())
        .append(" added, ")
        .append(changes.changed().size())
        .append(" changed, ")
        .append(changes.removed().size())
        .append(" removed.\n");
    appendIds(out, "added", changes.added());
    appendIds(out, "changed", changes.changed());
    appendIds(out, "removed", changes.removed());
    return out.toString();
  }

  private static void appendIds(StringBuilder out, String heading, List<String> ids) {
    if (!ids.isEmpty()) {
      out.append("  ").append(heading).append(": ").append(String.join(", ", ids)).append('\n');
    }
  }
}
