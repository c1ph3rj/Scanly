# Performance Budget

Archived note: these were the baseline targets used during Sprint 0 planning and validation.

## Preview inference

- analysis resolution: start around `640px` long side unless export benchmarking shows a better shape
- frame analysis rate: throttle to `4-8 FPS`
- detector latency target on a mid-range device: `<= 120 ms` p95 on CPU for correctness mode
- detector latency stretch target after acceleration tuning: `<= 60 ms` p95
- overlay update delay: should feel continuous and stable, not frame-perfect

## Auto-capture gating

- minimum stable duration before countdown: `700-1000 ms`
- countdown duration: `3 seconds`
- duplicate capture cooldown: `1000-1500 ms`

## Still-image processing

- still-image redetection target: `<= 250 ms` p95
- perspective correction + enhancement target: `<= 800 ms` p95 for a single page on a mid-range device
- end-to-end single-page capture-to-preview target: `<= 2 seconds`

## Memory and storage

- no step should require holding all full-size document pages in memory at once
- raw and processed images must be stored separately
- benchmark large exports with at least 15 pages before release

## Accuracy checks

For the validation image set, record:

- corner ordering errors
- missed detections
- partial-page false positives
- average corner pixel error after remapping to source image size

## Benchmark checklist

For each tested device, log:

- device model
- Android version
- runtime used
- model file size
- analysis resolution
- preview p50 and p95 latency
- still-image inference p50 and p95 latency
- any thermal throttling or crashes
