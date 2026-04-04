# ADR 0001: Runtime Selection

- Status: Accepted for Sprint 0

## Context

Scanly is an offline-first Android scanner. The model is currently trained in PyTorch and must be validated in a mobile-ready format before the rest of the scanning pipeline depends on it.

## Decision

Use the bundled LiteRT package family as the primary Android runtime path.

Validation order:

1. verify exported `.tflite` correctness on CPU
2. benchmark preview latency
3. evaluate accelerator support only after correctness is stable

Keep ONNX Runtime Mobile as a fallback only if the exported LiteRT artifact fails output-contract validation or is too slow after reasonable tuning.

## Consequences

- model behavior is versioned with the app instead of depending on Play services rollout state
- app size will be larger than a pure Play services approach
- Sprint 0 must include model export verification, not only Android UI work
