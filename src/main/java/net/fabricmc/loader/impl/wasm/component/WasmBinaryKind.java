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

package net.fabricmc.loader.impl.wasm.component;

/**
 * The two shapes a {@code .wasm} file can have. Both start with the same magic and are
 * distinguished by the {@code layer} field of the 8 byte preamble.
 */
public enum WasmBinaryKind {
	/**
	 * A plain WebAssembly module: {@code \0asm} followed by version 1, layer 0.
	 */
	CORE_MODULE,
	/**
	 * A WebAssembly component: {@code \0asm} followed by version 0x0d, layer 1.
	 */
	COMPONENT
}
