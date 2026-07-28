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

import java.util.Collection;

/**
 * Instantiates guest modules.
 *
 * <p>Implemented once, by the adapter around the bundled engine. The indirection exists so that the
 * engine's class files -- which target a newer Java version than the loader -- are only ever loaded
 * once a WebAssembly mod is actually present.
 */
public interface WasmEngine {
	/**
	 * @param coreModule a complete core module binary
	 * @param imports the host functions the guest may import
	 * @throws WasmExecutionException if the module is invalid or its imports cannot be satisfied
	 */
	WasmInstance instantiate(byte[] coreModule, Collection<WasmHostFunction> imports);

	/**
	 * @return a short description of the engine, for the log line written on first use
	 */
	String describe();
}
