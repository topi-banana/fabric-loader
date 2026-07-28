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

package net.fabricmc.loader.impl.wasm.runtime.chicory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.dylibso.chicory.runtime.HostFunction;
import com.dylibso.chicory.runtime.ImportFunction;
import com.dylibso.chicory.runtime.ImportValues;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.WasmFunctionHandle;
import com.dylibso.chicory.wasm.ChicoryException;
import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.WasmModule;
import com.dylibso.chicory.wasm.types.ValType;

import net.fabricmc.loader.impl.wasm.runtime.WasmEngine;
import net.fabricmc.loader.impl.wasm.runtime.WasmExecutionException;
import net.fabricmc.loader.impl.wasm.runtime.WasmHostFunction;
import net.fabricmc.loader.impl.wasm.runtime.WasmInstance;
import net.fabricmc.loader.impl.wasm.runtime.WasmValueType;

/**
 * The only class that references the WebAssembly engine.
 *
 * <p>Reached exclusively through {@code WasmEngineProvider}'s reflective lookup, so that the JVM
 * never has to load it -- or Chicory, whose class files target Java 11 -- unless a WebAssembly mod
 * is present.
 */
public final class ChicoryWasmEngine implements WasmEngine {
	public ChicoryWasmEngine() { }

	@Override
	public WasmInstance instantiate(byte[] coreModule, Collection<WasmHostFunction> imports) {
		WasmModule module;

		try {
			module = Parser.parse(coreModule);
		} catch (ChicoryException e) {
			throw new WasmExecutionException("failed to parse the core module: " + e.getMessage(), e);
		}

		List<ImportFunction> functions = new ArrayList<>(imports.size());
		// the adapter is handed to each host function body so that it can reach linear memory;
		// it is completed right after the instance exists, before any guest code can run
		final ChicoryWasmInstance[] self = new ChicoryWasmInstance[1];

		for (WasmHostFunction function : imports) {
			functions.add(toHostFunction(function, self));
		}

		try {
			Instance instance = Instance.builder(module)
					.withImportValues(ImportValues.builder().withFunctions(functions).build())
					.withStart(false) // the loader calls the guest's init export explicitly
					.build();

			self[0] = new ChicoryWasmInstance(instance);
			return self[0];
		} catch (ChicoryException e) {
			throw new WasmExecutionException("failed to instantiate the core module: " + e.getMessage(), e);
		}
	}

	@Override
	public String describe() {
		return "Chicory " + com.dylibso.chicory.wasm.Version.version();
	}

	private static HostFunction toHostFunction(final WasmHostFunction function, final ChicoryWasmInstance[] self) {
		final WasmHostFunction.Body body = function.getBody();

		return new HostFunction(function.getModule(), function.getName(),
				toValTypes(function.getParameterTypes()), toValTypes(function.getResultTypes()),
				new WasmFunctionHandle() {
					@Override
					public long[] apply(Instance instance, long... args) {
						return body.call(self[0], args);
					}
				});
	}

	private static List<ValType> toValTypes(WasmValueType[] types) {
		List<ValType> ret = new ArrayList<>(types.length);

		for (WasmValueType type : types) {
			ret.add(toValType(type));
		}

		return ret;
	}

	private static ValType toValType(WasmValueType type) {
		switch (type) {
		case I32:
			return ValType.I32;
		case I64:
			return ValType.I64;
		case F32:
			return ValType.F32;
		case F64:
			return ValType.F64;
		default:
			throw new IllegalArgumentException("unhandled value type " + type);
		}
	}
}
