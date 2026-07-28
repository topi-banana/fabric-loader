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

/**
 * Names and version numbers shared by the WebAssembly mod support.
 */
public final class WasmConstants {
	/**
	 * Extension of a WebAssembly mod file in the mods directory.
	 */
	public static final String WASM_EXTENSION = ".wasm";

	/**
	 * Key under {@code fabric.mod.json}'s {@code custom} object holding the hook declarations.
	 */
	public static final String CUSTOM_KEY = "fabric:wasm";

	/**
	 * Directory under the loader's cache directory holding the generated mod artifacts.
	 */
	public static final String CACHE_DIR_NAME = "wasmMods";

	/**
	 * Package the generated Mixin classes live in.
	 *
	 * <p>Deliberately outside {@code net.fabricmc.loader} so that generated classes loaded from a
	 * mod code source cannot collide with the loader's own packages.
	 */
	public static final String GENERATED_PACKAGE_ROOT = "net.fabricmc.wasmgen";

	/**
	 * Version of the {@code fabric:wasm} declaration format this loader understands.
	 */
	public static final int SUPPORTED_ABI_VERSION = 1;

	/**
	 * Java version the WebAssembly engine's class files require.
	 */
	public static final int MIN_JAVA_VERSION = 11;

	/**
	 * Bumped whenever the generator's output changes, so that stale caches are regenerated.
	 */
	public static final int GENERATOR_VERSION = 3;

	private WasmConstants() { }

	/**
	 * @return the Mixin configuration resource name generated for a mod
	 */
	public static String mixinConfigName(String modId) {
		return modId + ".wasm.mixins.json";
	}

	/**
	 * @return the package the generated Mixin classes of a mod live in
	 */
	public static String generatedPackage(String modId) {
		return GENERATED_PACKAGE_ROOT + "." + sanitizeModId(modId);
	}

	/**
	 * Turns a mod id into a valid Java identifier.
	 *
	 * <p>Mod ids match {@code [a-z][a-z0-9-_]{1,63}} (see {@code MetadataVerifier}), so they always
	 * start with a letter and only {@code -} needs replacing.
	 */
	public static String sanitizeModId(String modId) {
		return modId.replace('-', '_');
	}
}
