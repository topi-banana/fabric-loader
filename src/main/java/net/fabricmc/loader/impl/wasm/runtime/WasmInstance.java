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
 * An instantiated guest module.
 *
 * <p>Kept deliberately narrow: calling an export and reading or writing linear memory is everything
 * the Canonical ABI implementation needs, so this is the entire surface that has to be reimplemented
 * if the engine is ever swapped out. It is also the only surface compiled against the engine, which
 * keeps the engine's newer class file version off the ordinary startup path.
 *
 * <p>Implementations are not thread safe; callers hold the mod's instance lock.
 */
public interface WasmInstance {
	/**
	 * Calls an exported function.
	 *
	 * @param exportName the core export name
	 * @param args flattened arguments; floats in raw bit representation
	 * @return flattened results
	 * @throws WasmExecutionException if the guest traps or the export does not exist
	 */
	long[] call(String exportName, long[] args);

	boolean hasExport(String exportName);

	int readInt(int address);

	long readLong(int address);

	byte readByte(int address);

	byte[] readBytes(int address, int length);

	void writeInt(int address, int value);

	void writeLong(int address, long value);

	void writeByte(int address, byte value);

	void writeBytes(int address, byte[] data);

	/**
	 * @return the current size of linear memory in bytes
	 */
	int memoryByteSize();

	/**
	 * Releases the instance. Calling anything else afterwards is undefined.
	 */
	void close();
}
