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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps the hook ids baked into generated Mixin handlers back to the mod that declared them.
 *
 * <p>Hook ids are allocated globally and densely at generation time so that a handler can carry a
 * single {@code int} and the runtime can resolve it with an array index rather than a map lookup on
 * a path that may run every tick.
 */
public final class WasmModRegistry {
	private static final Map<String, WasmModEntry> MODS = new LinkedHashMap<>();
	private static final List<WasmModEntry> HOOK_OWNERS = new ArrayList<>();
	private static final List<Integer> HOOK_LOCAL_IDS = new ArrayList<>();

	private WasmModRegistry() { }

	/**
	 * Registers a mod and allocates a global id for each of its declared hooks.
	 *
	 * <p>Called from the generation stage, on one thread, before any hook can fire.
	 */
	static synchronized void register(WasmModEntry entry) {
		MODS.put(entry.getModId(), entry);

		for (int localId = 0; localId < entry.getSpec().getHooks().size(); localId++) {
			HOOK_OWNERS.add(entry);
			HOOK_LOCAL_IDS.add(localId);
		}
	}

	/**
	 * @return the global id the next registered hook of a mod will get
	 */
	static synchronized int nextGlobalHookId() {
		return HOOK_OWNERS.size();
	}

	/**
	 * @return the mod owning a global hook id
	 * @throws IndexOutOfBoundsException if no such hook was registered
	 */
	public static WasmModEntry getHookOwner(int globalHookId) {
		return HOOK_OWNERS.get(globalHookId);
	}

	/**
	 * @return the mod-local hook index, which is what the guest's dispatch export receives
	 */
	public static int getLocalHookId(int globalHookId) {
		return HOOK_LOCAL_IDS.get(globalHookId);
	}

	public static int getHookCount() {
		return HOOK_OWNERS.size();
	}

	public static WasmModEntry get(String modId) {
		return MODS.get(modId);
	}

	public static Collection<WasmModEntry> getMods() {
		return Collections.unmodifiableCollection(MODS.values());
	}

	public static boolean isEmpty() {
		return MODS.isEmpty();
	}
}
