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

import net.fabricmc.loader.impl.util.SystemProperties;

/**
 * Feature gate for WebAssembly mod support.
 *
 * <p>The WebAssembly engine's class files target a newer Java version than the loader itself, so
 * nothing on the ordinary startup path may reference it. Only the parts that run once a
 * {@code .wasm} has actually been found are allowed to, which is why the check lives here rather
 * than in the engine.
 */
public final class WasmSupport {
	private WasmSupport() { }

	public static boolean isDisabled() {
		return SystemProperties.isSet(SystemProperties.WASM_DISABLED);
	}

	/**
	 * @return true if {@code path} names a WebAssembly mod file
	 */
	public static boolean isWasmPath(Path path) {
		Path fileName = path.getFileName();
		return fileName != null && fileName.toString().endsWith(WasmConstants.WASM_EXTENSION);
	}

	/**
	 * @return the major version of the running JVM, normalising Java 8's {@code "1.8"} form
	 */
	public static int currentJavaVersion() {
		String version = System.getProperty("java.specification.version", "1.8").replaceFirst("^1\\.", "");

		try {
			return Integer.parseInt(version);
		} catch (NumberFormatException e) {
			return 0; // unparseable: treat as too old and let the caller report it
		}
	}

	public static boolean isJavaVersionSufficient() {
		return currentJavaVersion() >= WasmConstants.MIN_JAVA_VERSION;
	}
}
