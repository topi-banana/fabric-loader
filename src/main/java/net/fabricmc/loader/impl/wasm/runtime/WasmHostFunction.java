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
 * A function the host provides to the guest, described without naming any engine type.
 */
public final class WasmHostFunction {
	/**
	 * The body of a host function. Arguments and results are already flattened to core
	 * WebAssembly values; floats are carried in their raw bit representation.
	 */
	public interface Body {
		long[] call(WasmInstance instance, long[] args);
	}

	private static final WasmValueType[] NO_TYPES = new WasmValueType[0];

	private final String module;
	private final String name;
	private final WasmValueType[] parameterTypes;
	private final WasmValueType[] resultTypes;
	private final Body body;

	public WasmHostFunction(String module, String name, WasmValueType[] parameterTypes,
			WasmValueType[] resultTypes, Body body) {
		this.module = module;
		this.name = name;
		this.parameterTypes = parameterTypes != null ? parameterTypes : NO_TYPES;
		this.resultTypes = resultTypes != null ? resultTypes : NO_TYPES;
		this.body = body;
	}

	/**
	 * @return the import module name the guest uses, for example {@code fabric:mod/host}
	 */
	public String getModule() {
		return module;
	}

	public String getName() {
		return name;
	}

	public WasmValueType[] getParameterTypes() {
		return parameterTypes;
	}

	public WasmValueType[] getResultTypes() {
		return resultTypes;
	}

	public Body getBody() {
		return body;
	}

	@Override
	public String toString() {
		return module + "#" + name;
	}
}
