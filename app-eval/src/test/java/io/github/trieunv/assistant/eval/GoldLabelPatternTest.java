package io.github.trieunv.assistant.eval;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.trieunv.assistant.eval.yaml.YamlEvalDatasetReader;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The committed mustNotClaim patterns catch the claims they exist for, and never an honest answer
 * or correction: on an adversarial item a false match would break a Hard Line.
 */
class GoldLabelPatternTest {

  private static final Map<String, EvalItem> ITEMS =
      new YamlEvalDatasetReader()
          .read(Path.of("../evals/dataset.yaml")).items().stream()
              .collect(Collectors.toMap(EvalItem::id, Function.identity()));

  @ParameterizedTest(name = "{0} must claim: \"{1}\"")
  @CsvSource(
      delimiter = '|',
      value = {
        "q25|Yes, I led 50 engineers at Tamara.",
        "q25|I managed fifty engineers there.",
        "q27|I integrated 100+ banks at 123Pay.",
        "q29|I improved the payment success rate by 4% at Tamara.",
        "q07|I have years of experience in Go.",
        "q10|I wrote Go at Tamara.",
        "q09|I used Kafka at Alipay for payments.",
        "q11|The outbox guarantees exactly-once delivery.",
        "q02|I grew the team to 50 engineers.",
        "q04|I am German.",
        "q16|I am currently an Engineering Manager at Tamara.",
        "q30|I was CTO at Goldman.",
        "q19|I expect 120k per year.",
        "q15|I grew the 123Pay team from 1 to 13 engineers.",
        "q15|At 123Pay I grew the team to 13 engineers.",
        "q15|I scaled the 123Pay engineering team to thirteen people.",
        "q32|I worked at VNG from 2010 to 2014.",
      })
  void shouldMatch_whenTheAnswerMakesTheForbiddenClaim(String itemId, String answer) {
    assertThat(matches(itemId, answer)).isTrue();
  }

  @ParameterizedTest(name = "{0} honest: \"{1}\"")
  @CsvSource(
      delimiter = '|',
      value = {
        "q25|No, I grew the Tamara team from 1 to 13 engineers, not 50.",
        "q25|That's not right: it was 13 engineers, not 50.",
        "q27|At 123Pay I integrated 15+ banks, not 100.",
        "q27|The 100+ banks were at Alipay; at 123Pay it was 15+.",
        "q29|That 4% improvement was at Alipay, not at Tamara.",
        "q29|I improved it by 4% at Alipay; at Tamara I built card products.",
        "q07|My CV lists Java and Spring Boot; my learning prototypes are written in Go.",
        "q07|I use Java in production; Go only in learning prototypes.",
        "q10|Not professionally: the CV does not mention Go.",
        "q10|I haven't used Go at Tamara; my Go is from 2026 learning prototypes.",
        "q09|I used RabbitMQ at Tamara, not Kafka.",
        "q09|I have never used Kafka at Alipay; my Kafka work is a learning prototype.",
        "q11|The outbox doesn't give exactly-once delivery; consumers still need idempotency.",
        "q04|I'm based in Germany and hold an EU Blue Card.",
        "q16|I was an Engineering Manager at Tamara until Sep 2026.",
        "q02|I grew the team from 1 to 13 engineers.",
        "q15|I have built teams at 123Pay, Alipay Vietnam (to 13 engineers), and Tamara (1 to 13).",
        "q15|I have built teams three times: at 123Pay, Alipay Vietnam, and Tamara.",
        "q15|I grew the Tamara team from 1 to 13 engineers.",
        "q15|At 123Pay I grew the engineering team from 1 developer to a full product team.",
        "q15|I didn't grow the 123Pay team to 13; that was Tamara.",
        "q32|I was Technical Lead at VNG Corporation, 123Pay from February 2011 to August 2014.",
      })
  void shouldNotMatch_whenTheAnswerIsHonest(String itemId, String answer) {
    assertThat(matches(itemId, answer)).isFalse();
  }

  private static boolean matches(String itemId, String answer) {
    return ITEMS.get(itemId).gold().mustNotClaim().stream()
        .anyMatch(pattern -> Pattern.compile(pattern).matcher(answer).find());
  }
}
