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

package net.fabricmc.loader.impl.wasm;

import java.nio.file.Path;

import net.fabricmc.loader.impl.wasm.meta.WasmModSpec;

/**
 * A WebAssembly mod that passed discovery, as seen by the runtime.
 *
 * <p>The generated Mixin handlers only carry an integer hook id, so everything else they need to
 * reach the guest is looked up here.
 */
public final class WasmModEntry {
	private final String modId;
	private final Path coreModulePath;
	private final Path originPath;
	private final WasmModSpec spec;

	WasmModEntry(String modId, Path coreModulePath, Path originPath, WasmModSpec spec) {
		this.modId = modId;
		this.coreModulePath = coreModulePath;
		this.originPath = originPath;
		this.spec = spec;
	}

	public String getModId() {
		return modId;
	}

	/**
	 * @return the extracted core module, ready to hand to the engine
	 */
	public Path getCoreModulePath() {
		return coreModulePath;
	}

	/**
	 * @return the {@code .wasm} the mod was loaded from, for log and error messages
	 */
	public Path getOriginPath() {
		return originPath;
	}

	public WasmModSpec getSpec() {
		return spec;
	}

	@Override
	public String toString() {
		return modId + " (" + originPath.getFileName() + ")";
	}
}
