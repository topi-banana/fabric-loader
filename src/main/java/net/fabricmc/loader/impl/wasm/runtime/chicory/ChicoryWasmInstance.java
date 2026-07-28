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

import java.util.HashMap;
import java.util.Map;

import com.dylibso.chicory.runtime.ExportFunction;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Memory;
import com.dylibso.chicory.wasm.ChicoryException;

import net.fabricmc.loader.impl.wasm.runtime.WasmExecutionException;
import net.fabricmc.loader.impl.wasm.runtime.WasmInstance;

/**
 * Adapts a Chicory {@link Instance} to the loader's engine boundary.
 */
final class ChicoryWasmInstance implements WasmInstance {
	private static final long[] NO_ARGS = new long[0];

	private final Instance instance;
	private final Memory memory;
	private final Map<String, ExportFunction> exports = new HashMap<>();

	ChicoryWasmInstance(Instance instance) {
		this.instance = instance;
		this.memory = instance.memory();
	}

	@Override
	public long[] call(String exportName, long[] args) {
		ExportFunction function = exports.get(exportName);

		if (function == null) {
			try {
				function = instance.export(exportName);
			} catch (ChicoryException | IllegalArgumentException e) {
				throw new WasmExecutionException("guest does not export '" + exportName + "'", e);
			}

			exports.put(exportName, function);
		}

		try {
			long[] ret = function.apply(args != null ? args : NO_ARGS);
			return ret != null ? ret : NO_ARGS;
		} catch (ChicoryException e) {
			throw new WasmExecutionException("guest trapped in '" + exportName + "': " + e.getMessage(), e);
		}
	}

	@Override
	public boolean hasExport(String exportName) {
		if (exports.containsKey(exportName)) return true;

		try {
			exports.put(exportName, instance.export(exportName));
			return true;
		} catch (ChicoryException | IllegalArgumentException e) {
			return false;
		}
	}

	@Override
	public int readInt(int address) {
		return memory.readInt(address);
	}

	@Override
	public long readLong(int address) {
		return memory.readLong(address);
	}

	@Override
	public byte readByte(int address) {
		return memory.read(address);
	}

	@Override
	public byte[] readBytes(int address, int length) {
		return memory.readBytes(address, length);
	}

	@Override
	public void writeInt(int address, int value) {
		memory.writeI32(address, value);
	}

	@Override
	public void writeLong(int address, long value) {
		memory.writeLong(address, value);
	}

	@Override
	public void writeByte(int address, byte value) {
		memory.writeByte(address, value);
	}

	@Override
	public void writeBytes(int address, byte[] data) {
		memory.write(address, data);
	}

	@Override
	public int memoryByteSize() {
		return memory.pages() * Memory.PAGE_SIZE;
	}

	@Override
	public void close() {
		exports.clear();
	}
}
