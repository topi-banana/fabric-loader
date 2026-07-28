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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.fabricmc.loader.impl.wasm.WasmModEntry;
import net.fabricmc.loader.impl.wasm.WasmModRegistry;

/**
 * Routes a global hook id to its mod's guest instance.
 *
 * <p>Generated Mixin handlers carry nothing but an integer, so this is where that integer becomes a
 * guest call. Instances are started on first use rather than at load time, so a mod whose hooks
 * never fire costs nothing and a broken guest fails at a point that names the hook responsible.
 */
public final class WasmHookDispatcher {
	private static final Map<String, WasmModInstance> INSTANCES = new HashMap<>();

	/**
	 * Return type descriptors, cached because a substituted return value has to be boxed on a path
	 * that may run every tick. Concurrent because hooks fire from several game threads.
	 */
	private static final Map<Integer, String> RETURN_TYPES = new ConcurrentHashMap<>();

	private WasmHookDispatcher() { }

	/**
	 * Runs the hook and returns what the guest asked for.
	 *
	 * <p>Never throws: a hook is called from woven game code, where an exception would surface as a
	 * crash in an unrelated place. Failures disable the mod and are reported instead.
	 */
	public static HookOutcome dispatch(int globalHookId) {
		HookArgs args = HookArgs.current();

		try {
			WasmModInstance instance = instanceFor(globalHookId);
			return instance.dispatch(WasmModRegistry.getLocalHookId(globalHookId), args);
		} catch (Throwable t) {
			Log.error(LogCategory.GENERAL, "WebAssembly hook %d failed", globalHookId, t);
			args.clear();
			return HookOutcome.PROCEED_OUTCOME;
		}
	}

	/**
	 * @return descriptor of the return type of the method a hook injects into, for boxing a
	 *     substituted return value
	 */
	public static String returnTypeOf(int globalHookId) {
		String cached = RETURN_TYPES.get(globalHookId);
		if (cached != null) return cached;

		WasmModEntry entry = WasmModRegistry.getHookOwner(globalHookId);
		String descriptor = entry.getSpec().getHooks().get(WasmModRegistry.getLocalHookId(globalHookId))
				.getTargetDescriptor();
		String ret = descriptor.substring(descriptor.indexOf(')') + 1);

		RETURN_TYPES.put(globalHookId, ret);
		return ret;
	}

	private static synchronized WasmModInstance instanceFor(int globalHookId) {
		WasmModEntry entry = WasmModRegistry.getHookOwner(globalHookId);
		WasmModInstance ret = INSTANCES.get(entry.getModId());

		if (ret == null) {
			ret = new WasmModInstance(entry);
			// registered before start() so that a hook re-entered during initialisation finds the
			// instance rather than building a second one
			INSTANCES.put(entry.getModId(), ret);
			ret.start();
		}

		return ret;
	}
}
