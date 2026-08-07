# examples

Runnable example apps — also the primary "how do I use this"
documentation and the GraalVM native-image compile targets. Each has headless `Pilot`
end-to-end tests in its `test` submodule.

| Example | Run | Shows |
|---|---|---|
| `hello-world` | `./mill examples.hello-world.run` | static panel/text through the DSL |
| `counter` | `./mill examples.counter.run` | signal update → re-render cycle, keybindings |
| `todo-list` | `./mill examples.todo-list.run` | `input` + `list`, Tab focus switching |
| `dashboard` | `./mill examples.dashboard.run` | `gauge`/`sparkline`/`chart`, tick-rate animation |
| `form-demo` | `./mill examples.form-demo.run` | `deriveForm` + `Field.mapValidated` validation |
| `weather` | `./mill examples.weather.run` | live public HTTP API call bridged into `Signal` via `RenderThread.runOnRenderThread` |
| `procmon` | `./mill examples.procmon.run` | a sortable/filterable table over refreshing data, with a selection that survives the refresh |
| `airsensor` | `./mill examples.airsensor.run` | polling on a timer, threshold bands, trend arrows, a loading/ready/error state machine |
| `loadtest` | `./mill examples.loadtest.run` | concurrent background work streamed back to the render thread, with a live histogram and percentiles |

Each of `procmon`, `airsensor` and `loadtest` has a step-by-step guide that builds it from
nothing: see [Build a real app](../website/docs/build-a-process-monitor.md) in the docs.

Native binaries: `./mill show examples.<name>.nativeImage` (GraalVM community 23.0.1,
`--no-fallback`, no reflect-config needed). CI builds every example except the three that
use `java.net.http` — `weather`, `airsensor` and `loadtest` — which are excluded from that
job rather than carrying the extra native configuration an HTTPS client needs.
