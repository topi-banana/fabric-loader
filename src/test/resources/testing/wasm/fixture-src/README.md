# WebAssembly test fixtures

`minimalComponent.wasm` in the parent directory is a real WebAssembly component produced by
`wasm-tools`, with a `fabric.mod.json` custom section appended. It is committed as a binary
because the build only requires a JDK -- no WebAssembly toolchain runs during `./gradlew build`.

Regenerate it with `wasm-tools` 1.254 or later (`cargo install wasm-tools`):

```sh
wasm-tools parse core.wat -o core.wasm
wasm-tools component embed test.wit core.wasm -o embedded.wasm --world mod
wasm-tools component new embedded.wasm -o component.wasm
wasm-tools validate --features component-model component.wasm
```

Then append the metadata custom section. `wasm-tools` has no subcommand for adding an arbitrary
custom section, so do it by hand -- a custom section is `0x00`, a LEB128 content length, a LEB128
name length, the name, and the payload, and it is valid at the end of a component:

```sh
python3 - <<'PY'
import json

def leb(n):
    out = bytearray()
    while True:
        b = n & 0x7f
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            break
    return bytes(out)

meta = json.dumps({
    "schemaVersion": 1,
    "id": "wasmtestmod",
    "version": "1.0.0",
    "name": "WASM Test Mod",
    "depends": {"fabricloader": ">=0.19.3"},
    "custom": {"fabric:wasm": {"abi": 1, "hooks": []}},
}, separators=(",", ":")).encode()

name = b"fabric.mod.json"
content = leb(len(name)) + name + meta
section = bytes([0]) + leb(len(content)) + content

open("minimalComponent.wasm", "wb").write(open("component.wasm", "rb").read() + section)
PY
wasm-tools validate --features component-model minimalComponent.wasm
wasm-tools objdump minimalComponent.wasm
```

The fixture deliberately exercises the section kinds a real toolchain emits that the loader has to
skip over: `core instances`, `component alias`, `component types`, `canonical functions`,
`component exports`, and the `component-name` and `producers` custom sections.

## Engine level fixtures

`memoryStress.wasm` and `opcodeStress.wasm` are **bare core modules**, not components. They are fed
straight to the engine, bypassing the decoder, and exist to pin down what the release jar's
ProGuard shrink pass is allowed to remove from the bundled engine.

```sh
wasm-tools parse memoryStress.wat -o memoryStress.wasm
wasm-tools parse opcodeStress.wat  -o opcodeStress.wasm
wasm-tools validate memoryStress.wasm opcodeStress.wasm
```

The shrink pass drops the engine's ahead-of-time compiler, its alternative memory implementation,
its allocation strategies and its `BitOps` helper, because nothing the loader ships reaches them.
These two modules cover bulk memory, memory growth, the integer bit operations, the float
operations and signed/unsigned division, which is the evidence that the surviving code path is
complete. Verified by hand against `build/libs/fabric-loader-<version>.jar`; the tests here only
cover the unshrunk class path, so `runProductionAutoTestClient` stays the gate for the release jar.
