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
 * Per-thread scratch space holding the arguments of the hook currently being dispatched.
 *
 * <p>Exists so that a hook firing on every tick allocates nothing: the generated handler pushes its
 * parameters one at a time into these parallel arrays instead of building an {@code Object[]}, which
 * also avoids boxing every primitive. Reset by {@link #begin}, so an exception escaping mid-push
 * cannot leave stale state behind for the next invocation.
 */
public final class HookArgs {
	private static final int INITIAL_CAPACITY = 8;

	private static final ThreadLocal<HookArgs> CURRENT = new ThreadLocal<HookArgs>() {
		@Override
		protected HookArgs initialValue() {
			return new HookArgs();
		}
	};

	private byte[] kinds = new byte[INITIAL_CAPACITY];
	private long[] bits = new long[INITIAL_CAPACITY];
	private Object[] refs = new Object[INITIAL_CAPACITY];
	private int size;
	private int hookId = -1;

	private HookArgs() { }

	public static HookArgs current() {
		return CURRENT.get();
	}

	public void begin(int hookId) {
		this.hookId = hookId;
		clear();
	}

	/**
	 * Drops references so that a completed invocation cannot pin game objects alive.
	 */
	public void clear() {
		for (int i = 0; i < size; i++) {
			refs[i] = null;
		}

		size = 0;
	}

	public int getHookId() {
		return hookId;
	}

	public int size() {
		return size;
	}

	public ValueKind kindAt(int index) {
		return ValueKind.byDiscriminant(kinds[index]);
	}

	public long bitsAt(int index) {
		return bits[index];
	}

	public Object refAt(int index) {
		return refs[index];
	}

	public void pushObject(Object value) {
		int i = grow();
		kinds[i] = (byte) ValueKind.OBJECT.ordinal();
		refs[i] = value;
	}

	public void pushInt(int value) {
		int i = grow();
		kinds[i] = (byte) ValueKind.I32.ordinal();
		bits[i] = value;
	}

	public void pushLong(long value) {
		int i = grow();
		kinds[i] = (byte) ValueKind.I64.ordinal();
		bits[i] = value;
	}

	public void pushFloat(float value) {
		int i = grow();
		kinds[i] = (byte) ValueKind.F32.ordinal();
		bits[i] = Float.floatToRawIntBits(value) & 0xffffffffL;
	}

	public void pushDouble(double value) {
		int i = grow();
		kinds[i] = (byte) ValueKind.F64.ordinal();
		bits[i] = Double.doubleToRawLongBits(value);
	}

	private int grow() {
		if (size == kinds.length) {
			int capacity = kinds.length * 2;
			kinds = java.util.Arrays.copyOf(kinds, capacity);
			bits = java.util.Arrays.copyOf(bits, capacity);
			refs = java.util.Arrays.copyOf(refs, capacity);
		}

		return size++;
	}
}
