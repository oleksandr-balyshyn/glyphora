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

Native binaries: `./mill show examples.<name>.nativeImage` (GraalVM community 23.0.1,
`--no-fallback`, no reflect-config needed).
