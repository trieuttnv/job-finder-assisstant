package io.github.trieunv.assistant.application.port.out;

import java.util.Map;

/** What the previous successful run loaded, as Entry id to fingerprint; kept local. */
public interface KnowledgeStatePort {

  /** Empty when there was no previous run. */
  Map<String, String> previousFingerprints();

  void save(Map<String, String> fingerprints);
}
