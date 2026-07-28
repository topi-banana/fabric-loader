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

package net.fabricmc.loader.impl.wasm.meta;

/**
 * Thrown when a mod's {@code custom["fabric:wasm"]} block is missing, malformed or declares
 * something the loader cannot generate.
 */
public final class WasmMetadataException extends Exception {
	private static final long serialVersionUID = 1L;

	public WasmMetadataException(String message) {
		super(message);
	}
}
