# ADR 0003: Dependency Injection Strategy

- Status: Accepted for Sprint 0

## Context

The app will need replaceable implementations for camera, repositories, ML inference, image processing, and export. Those dependencies should be swappable in tests and easy to evolve.

## Decision

Adopt Hilt in Sprint 1 as the application DI framework.

Before Hilt lands, Sprint 0 introduces interface-first contracts so the runtime spike does not hardwire future implementation choices.

## Consequences

- repositories and processors can be bound to interfaces from the start
- instrumentation tests can replace production services later
- MainActivity can stay thin once real screens arrive
