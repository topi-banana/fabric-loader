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

import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.fabricmc.loader.impl.wasm.WasmConstants;
import net.fabricmc.loader.impl.wasm.WasmSupport;

/**
 * Loads the engine adapter on first use.
 *
 * <p>The adapter and the engine it wraps have class files targeting a newer Java version than the
 * loader, so the reference has to stay reflective: a static one would make every launch on an older
 * JVM fail during class verification, whether or not any WebAssembly mod is installed.
 */
public final class WasmEngineProvider {
	private static final String ADAPTER_CLASS = "net.fabricmc.loader.impl.wasm.runtime.chicory.ChicoryWasmEngine";

	private static volatile WasmEngine engine;

	private WasmEngineProvider() { }

	public static WasmEngine get() {
		WasmEngine ret = engine;
		if (ret != null) return ret;

		synchronized (WasmEngineProvider.class) {
			if (engine == null) engine = load();

			return engine;
		}
	}

	private static WasmEngine load() {
		if (!WasmSupport.isJavaVersionSufficient()) {
			throw new WasmExecutionException(String.format(
					"WebAssembly mods need Java %d or later, but the game is running on Java %d",
					WasmConstants.MIN_JAVA_VERSION, WasmSupport.currentJavaVersion()));
		}

		try {
			// the loader's own class loader, explicitly: a hook reaches this from a class woven into
			// the game, so the context class loader at that point is the game's, which cannot see
			// the relocated engine
			Class<?> adapter = Class.forName(ADAPTER_CLASS, true, WasmEngineProvider.class.getClassLoader());
			WasmEngine ret = (WasmEngine) adapter.getDeclaredConstructor().newInstance();
			Log.info(LogCategory.GENERAL, "Initialized WebAssembly engine: %s", ret.describe());
			return ret;
		} catch (ReflectiveOperationException | LinkageError e) {
			throw new WasmExecutionException("Failed to initialize the WebAssembly engine", e);
		}
	}
}
