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

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

/**
 * The host functions a guest may import.
 *
 * <p>Only logging is implemented so far. The reflective accessors of the {@code fabric:mod} world
 * are declared in the interface but not yet bound; a guest importing them fails to instantiate with
 * the engine's own unresolved import error, which names the missing function.
 */
public final class HostBindings {
	/**
	 * Import module name matching {@code interface log} of the {@code fabric:mod} world.
	 */
	private static final String LOG_MODULE = "fabric:mod/log";

	private static final LogCategory GUEST = LogCategory.create("Wasm");

	private HostBindings() { }

	public static List<WasmHostFunction> create(final WasmModInstance owner) {
		List<WasmHostFunction> ret = new ArrayList<>();
		ret.add(logWrite(owner));
		return ret;
	}

	/**
	 * {@code write: func(lvl: level, message: string)}.
	 */
	private static WasmHostFunction logWrite(final WasmModInstance owner) {
		return new WasmHostFunction(LOG_MODULE, "write",
				new WasmValueType[] {WasmValueType.I32, WasmValueType.I32, WasmValueType.I32},
				new WasmValueType[0],
				new WasmHostFunction.Body() {
					@Override
					public long[] call(WasmInstance instance, long[] args) {
						int level = (int) args[0];
						int pointer = (int) args[1];
						int length = (int) args[2];

						String message = length > 0
								? new String(instance.readBytes(pointer, length), CanonicalAbi.UTF_8) : "";

						write(level, owner.getEntry().getModId(), message);
						return new long[0];
					}
				});
	}

	private static void write(int level, String modId, String message) {
		switch (level) {
		case 0:
			Log.error(GUEST, "[%s] %s", modId, message);
			break;
		case 1:
			Log.warn(GUEST, "[%s] %s", modId, message);
			break;
		case 2:
			Log.info(GUEST, "[%s] %s", modId, message);
			break;
		case 3:
			Log.debug(GUEST, "[%s] %s", modId, message);
			break;
		default:
			Log.trace(GUEST, "[%s] %s", modId, message);
			break;
		}
	}
}
