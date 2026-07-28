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
 * The parts of a WebAssembly component that the loader uses: the embedded {@code fabric.mod.json}
 * and the core module that carries the actual code.
 */
public final class ComponentImage {
	private final byte[] metadata;
	private final byte[] coreModule;
	private final CoreModuleInfo coreModuleInfo;

	ComponentImage(byte[] metadata, byte[] coreModule, CoreModuleInfo coreModuleInfo) {
		this.metadata = metadata;
		this.coreModule = coreModule;
		this.coreModuleInfo = coreModuleInfo;
	}

	/**
	 * @return the raw bytes of the embedded {@code fabric.mod.json}, or null if the component has none
	 */
	public byte[] getMetadata() {
		return metadata;
	}

	/**
	 * @return the complete core module binary, ready to hand to the WebAssembly engine
	 */
	public byte[] getCoreModule() {
		return coreModule;
	}

	public CoreModuleInfo getCoreModuleInfo() {
		return coreModuleInfo;
	}
}
