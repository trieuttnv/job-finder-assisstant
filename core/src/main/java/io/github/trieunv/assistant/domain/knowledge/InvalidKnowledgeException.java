package io.github.trieunv.assistant.domain.knowledge;

import io.github.trieunv.assistant.domain.exception.AssistantDomainException;

/**
 * The knowledge base breaks an ingest check. {@code target} names what to fix: an Entry id (or the
 * file position of an Entry with no id), or a Source id.
 */
public class InvalidKnowledgeException extends AssistantDomainException {

  private final String target;
  private final KnowledgeProblem problem;

  private InvalidKnowledgeException(
      String kind, String target, KnowledgeProblem problem, String reason) {
    super(kind + " " + target + ": " + reason);
    this.target = target;
    this.problem = problem;
  }

  public static InvalidKnowledgeException ofEntry(
      String target, KnowledgeProblem problem, String reason) {
    return new InvalidKnowledgeException("Entry", target, problem, reason);
  }

  public static InvalidKnowledgeException ofSource(
      String sourceId, KnowledgeProblem problem, String reason) {
    return new InvalidKnowledgeException("Source", sourceId, problem, reason);
  }

  public String target() {
    return target;
  }

  public KnowledgeProblem problem() {
    return problem;
  }
}
