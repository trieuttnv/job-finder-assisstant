package io.github.trieunv.assistant.domain.knowledge;

import java.util.Set;

/**
 * What a question names that retrieval can narrow by: employers (as written in Entries' employer
 * field), technologies and Entry types. {@code typeOnly} means the question asks for a type and
 * nothing else ("Tell me about your education"), so Entries of that type are the answer even when
 * none of their words match.
 */
public record QuestionScope(
    Set<String> employers, Set<String> technologies, Set<EntryType> types, boolean typeOnly) {

  public QuestionScope {
    employers = Set.copyOf(employers);
    technologies = Set.copyOf(technologies);
    types = Set.copyOf(types);
  }
}
