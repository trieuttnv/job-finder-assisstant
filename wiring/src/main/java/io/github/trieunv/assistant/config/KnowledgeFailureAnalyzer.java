package io.github.trieunv.assistant.config;

import io.github.trieunv.assistant.domain.knowledge.InvalidKnowledgeException;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/** Startup stops on a broken knowledge base with the Entry id and reason, not a stack trace. */
class KnowledgeFailureAnalyzer extends AbstractFailureAnalyzer<InvalidKnowledgeException> {

  @Override
  protected FailureAnalysis analyze(Throwable rootFailure, InvalidKnowledgeException cause) {
    return new FailureAnalysis(
        "The knowledge base failed an ingest check. " + cause.getMessage(),
        "Fix the Entry file or Snapshot under KNOWLEDGE_DIR, then run again.",
        cause);
  }
}
