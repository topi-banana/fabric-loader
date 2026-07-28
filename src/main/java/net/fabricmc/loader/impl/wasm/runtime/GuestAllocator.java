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
 * Allocation inside the guest's linear memory, as the Canonical ABI defines it.
 *
 * <p>Backed by the guest's own {@code cabi_realloc} export: the host must not carve up linear
 * memory itself, because the guest's allocator owns it.
 */
public interface GuestAllocator {
	/**
	 * @param oldPtr previous allocation, or 0 for a fresh one
	 * @param oldSize size of the previous allocation, or 0
	 * @param align required alignment, a power of two
	 * @param newSize requested size
	 * @return address of the allocation
	 */
	int realloc(int oldPtr, int oldSize, int align, int newSize);
}
