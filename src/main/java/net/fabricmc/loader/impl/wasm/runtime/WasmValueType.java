/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.loader.impl.wasm.runtime;

/**
 * The core WebAssembly number types, which are the only types that cross the engine boundary.
 *
 * <p>Everything the Canonical ABI defines is flattened to these before a call, so the loader never
 * needs to name the engine's own type model.
 */
public enum WasmValueType {
	I32,
	I64,
	F32,
	F64
}
