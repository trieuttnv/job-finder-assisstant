package io.github.trieunv.assistant.application.port.out;

import io.github.trieunv.assistant.domain.knowledge.Entry;
import java.util.List;

public interface KnowledgeBasePort {

  List<Entry> loadEntries();
}
