package io.github.trieunv.assistant.application.dto;

/** A model's price in US dollars per million input and output tokens. */
public record TokenPrice(double inUsdPerMtok, double outUsdPerMtok) {

  public double costUsd(int inputTokens, int outputTokens) {
    return (inputTokens * inUsdPerMtok + outputTokens * outUsdPerMtok) / 1_000_000;
  }
}
