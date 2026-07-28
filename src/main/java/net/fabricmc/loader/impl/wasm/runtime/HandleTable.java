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

import java.util.Arrays;

/**
 * Maps Java objects to the opaque integer handles a guest sees.
 *
 * <p>Handles are scoped to a single hook dispatch: {@link #openScope()} marks the table and
 * {@link #closeScope(int)} releases everything handed out since, so a guest cannot pin game objects
 * alive by holding on to handles and there is nothing to leak. References are strong while a scope
 * is open, which keeps the garbage collector out of the picture entirely.
 *
 * <p>A handle packs a generation counter above its index. Validating the generation on every lookup
 * turns "the guest cached a handle from a previous hook" into a clean error instead of silently
 * operating on whatever object now occupies that slot.
 *
 * <p>Not synchronised: every guest interaction happens under the mod's instance lock, so that lock
 * is this table's lock too.
 */
public final class HandleTable {
	/**
	 * Handle 0 always means Java {@code null}, matching the world's documentation.
	 */
	public static final int NULL_HANDLE = 0;

	private static final int INDEX_BITS = 20;
	private static final int INDEX_MASK = (1 << INDEX_BITS) - 1;
	private static final int MAX_ENTRIES = INDEX_MASK; // index 0 is reserved for the null handle
	private static final int GENERATION_MASK = 0x7ff; // 11 bits, keeping the sign bit clear

	private Object[] entries = new Object[16];
	private int[] generations = new int[16];
	private int size = 1; // slot 0 is never handed out
	private int generation = 1;

	/**
	 * @return a mark to pass to {@link #closeScope(int)}
	 */
	public int openScope() {
		return size;
	}

	/**
	 * Releases every handle allocated since {@code mark} and invalidates them.
	 */
	public void closeScope(int mark) {
		for (int i = mark; i < size; i++) {
			entries[i] = null;
		}

		size = mark;
		generation = (generation + 1) & GENERATION_MASK;
		if (generation == 0) generation = 1;
	}

	/**
	 * @return a fresh handle for {@code value}, or {@link #NULL_HANDLE} if it is null
	 */
	public int allocate(Object value) {
		if (value == null) return NULL_HANDLE;

		if (size > MAX_ENTRIES) {
			throw new WasmExecutionException("too many live handles (" + size + "); a hook is passing an "
					+ "unreasonable number of objects to the guest");
		}

		if (size == entries.length) {
			int capacity = Math.min(entries.length * 2, MAX_ENTRIES + 1);
			entries = Arrays.copyOf(entries, capacity);
			generations = Arrays.copyOf(generations, capacity);
		}

		int index = size++;
		entries[index] = value;
		generations[index] = generation;

		return (generation << INDEX_BITS) | index;
	}

	/**
	 * @return the object behind a handle
	 * @throws WasmExecutionException if the handle is stale or was never valid
	 */
	public Object resolve(int handle) {
		Object ret = resolveOrNull(handle);

		if (ret == null && handle != NULL_HANDLE) {
			throw new WasmExecutionException("guest used handle " + handle + ", which is no longer valid; "
					+ "handles are only usable within the hook that produced them");
		}

		return ret;
	}

	/**
	 * @return the object behind a handle, or null if the handle is null, stale or out of range
	 */
	public Object resolveOrNull(int handle) {
		if (handle == NULL_HANDLE) return null;

		int index = handle & INDEX_MASK;
		if (index <= 0 || index >= size) return null;
		if (generations[index] != ((handle >>> INDEX_BITS) & GENERATION_MASK)) return null;

		return entries[index];
	}

	/**
	 * @return how many handles are currently live, excluding the reserved null slot
	 */
	public int liveCount() {
		return size - 1;
	}
}
