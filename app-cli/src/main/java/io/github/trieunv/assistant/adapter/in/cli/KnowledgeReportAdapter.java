package io.github.trieunv.assistant.adapter.in.cli;

import io.github.trieunv.assistant.application.dto.LoadedKnowledge;
import io.github.trieunv.assistant.application.port.in.LoadKnowledgeUseCase;
import java.io.PrintStream;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Once every bean has started, prints the id report to stderr when ids changed and records this run
 * as the previous one. A startup that fails earlier records nothing, so its report is shown again.
 */
@Component
@RequiredArgsConstructor
public class KnowledgeReportAdapter {

  private final LoadKnowledgeUseCase loadKnowledge;
  private final LoadedKnowledge knowledge;
  private final IdChangesRenderer renderer = new IdChangesRenderer();
  private final PrintStream err = System.err;

  @EventListener(ApplicationStartedEvent.class)
  public void reportAndRecord() {
    if (!knowledge.getChanges().isEmpty()) {
      err.print(renderer.render(knowledge));
    }
    loadKnowledge.recordAsPreviousRun(knowledge);
  }
}
