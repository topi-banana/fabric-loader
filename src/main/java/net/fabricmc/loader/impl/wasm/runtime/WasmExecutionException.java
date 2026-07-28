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
 * Thrown when a guest traps, runs out of memory, or is asked for something it does not export.
 *
 * <p>Unchecked because it can surface from a generated Mixin handler, which has no place to declare it.
 */
public class WasmExecutionException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public WasmExecutionException(String message) {
		super(message);
	}

	public WasmExecutionException(String message, Throwable cause) {
		super(message, cause);
	}
}
