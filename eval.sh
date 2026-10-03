#!/usr/bin/env bash
# ./eval.sh [--provider anthropic|deepseek|baseline] [--reps N] [--split dev|heldout|all]
# runs every evaluation question through the same Ask use case as ./ask, scores it with code only,
# prints the metric table and writes evals/results/<date>-<provider>-<model>/.
# Exit code: 0 for a completed run, 1 when a Hard Line fails, 2 when it refuses to run.
set -euo pipefail
cd "$(dirname "$0")"

# .env fills in settings; anything already set in the shell wins over it.
if [[ -f .env ]]; then
  already_set="$(export -p)"
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
  eval "$already_set"
fi

args=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --provider) export MODEL_PROVIDER="$2"; shift 2 ;;
    --baseline) export MODEL_PROVIDER=baseline; shift ;;
    *) args+=("$1"); shift ;;
  esac
done
# A model id left over in .env belongs to one provider only; the runner records what it used.
if [[ "${MODEL_PROVIDER:-anthropic}" != "anthropic" ]]; then
  export MODEL_ID="${EVAL_MODEL_ID:-}"
fi

# Each run's traces stay local (git-ignored), in their own folder, so the scorer reads only them.
export TRACE_DIR="${EVAL_TRACE_DIR:-traces/eval/$(date -u +%Y%m%dT%H%M%SZ)}"
commit="$(git rev-parse --short HEAD 2>/dev/null || echo unknown)"
if [[ -n "$(git status --porcelain -- core adapters-out wiring app-eval knowledge prompts evals/dataset.yaml 2>/dev/null)" ]]; then
  commit="${commit}-dirty"
fi
export EVAL_COMMIT="$commit"

jar=app-eval/target/assistant-eval.jar
if [[ ! -f "$jar" ]] || [[ -n "$(find pom.xml core adapters-out wiring app-eval -newer "$jar" -type f \
    \( -name '*.java' -o -name '*.xml' -o -name '*.yml' \) -not -path '*/target/*' -print -quit)" ]]; then
  echo "Building..." >&2
  ./mvnw -q -DskipTests -pl app-eval -am package >&2
fi

exec java -jar "$jar" ${args[@]+"${args[@]}"}
