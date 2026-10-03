package io.github.trieunv.assistant.application.port.out;

import io.github.trieunv.assistant.application.dto.AskTrace;

public interface TraceSinkPort {

  void append(AskTrace trace);
}
