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
 * The {@code value-kind} enum of the {@code fabric:mod} world.
 *
 * <p>The ordinals are part of the guest ABI: they are what gets written into linear memory, so they
 * must stay in step with the {@code .wit} definition and may only be appended to.
 */
public enum ValueKind {
	NONE,
	BOOL,
	I8,
	U16_CHAR,
	I16,
	I32,
	I64,
	F32,
	F64,
	OBJECT;

	private static final ValueKind[] VALUES = values();

	/**
	 * @return the kind with the given discriminant, or null if the guest sent an unknown one
	 */
	public static ValueKind byDiscriminant(int discriminant) {
		return discriminant >= 0 && discriminant < VALUES.length ? VALUES[discriminant] : null;
	}

	/**
	 * @return whether values of this kind carry a handle rather than a number
	 */
	public boolean isObject() {
		return this == OBJECT;
	}
}
