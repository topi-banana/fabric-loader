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

import org.objectweb.asm.Type;

/**
 * What a guest asked the host to do after a hook returned.
 *
 * <p>Mirrors the {@code outcome} variant of the {@code fabric:mod} world. Instances are immutable
 * and the common no-op cases are shared, so an uneventful hook allocates nothing.
 */
public final class HookOutcome {
	/**
	 * Discriminants of the {@code outcome} variant. Part of the guest ABI; append only.
	 */
	public static final int PROCEED = 0;
	public static final int CANCEL = 1;
	public static final int RETURN_VALUE = 2;
	public static final int REPLACE = 3;
	public static final int SUPPLY = 4;
	public static final int PASSTHROUGH = 5;
	public static final int SKIP = 6;
	public static final int FAILED = 7;

	/**
	 * Shared instance for "do nothing", which is the overwhelmingly common answer.
	 */
	public static final HookOutcome PROCEED_OUTCOME = new HookOutcome(PROCEED, ValueKind.NONE, 0L, null);
	public static final HookOutcome CANCEL_OUTCOME = new HookOutcome(CANCEL, ValueKind.NONE, 0L, null);

	private static final ThreadLocal<HookOutcome> STASH = new ThreadLocal<>();

	private final int discriminant;
	private final ValueKind valueKind;
	private final long bits;
	private final Object ref;

	HookOutcome(int discriminant, ValueKind valueKind, long bits, Object ref) {
		this.discriminant = discriminant;
		this.valueKind = valueKind;
		this.bits = bits;
		this.ref = ref;
	}

	public int getDiscriminant() {
		return discriminant;
	}

	public ValueKind getValueKind() {
		return valueKind;
	}

	/**
	 * @return whether the target's execution should stop here
	 */
	public boolean isCancel() {
		return discriminant == CANCEL || discriminant == RETURN_VALUE;
	}

	/**
	 * @return whether the guest supplied a value to use in place of something
	 */
	public boolean hasValue() {
		switch (discriminant) {
		case RETURN_VALUE:
		case REPLACE:
		case SUPPLY:
			return valueKind != ValueKind.NONE;
		default:
			return false;
		}
	}

	public int asInt() {
		return (int) bits;
	}

	public long asLong() {
		return bits;
	}

	public float asFloat() {
		return Float.intBitsToFloat((int) bits);
	}

	public double asDouble() {
		return Double.longBitsToDouble(bits);
	}

	public Object asObject() {
		return ref;
	}

	/**
	 * Boxes the value for Mixin's {@code CallbackInfoReturnable.setReturnValue}, which is the one
	 * place on the hot path where boxing is unavoidable.
	 *
	 * @param returnType descriptor of the target method's return type
	 */
	public Object asBoxed(String returnType) {
		if (returnType == null) return ref;

		switch (Type.getType(returnType).getSort()) {
		case Type.BOOLEAN:
			return Boolean.valueOf(asInt() != 0);
		case Type.BYTE:
			return Byte.valueOf((byte) asInt());
		case Type.CHAR:
			return Character.valueOf((char) asInt());
		case Type.SHORT:
			return Short.valueOf((short) asInt());
		case Type.INT:
			return Integer.valueOf(asInt());
		case Type.LONG:
			return Long.valueOf(asLong());
		case Type.FLOAT:
			return Float.valueOf(asFloat());
		case Type.DOUBLE:
			return Double.valueOf(asDouble());
		default:
			return ref;
		}
	}

	/**
	 * Holds a redirect hook's outcome between the status check and the value read, so that the
	 * generated handler needs only one guest call.
	 */
	public static void stash(HookOutcome outcome) {
		STASH.set(outcome);
	}

	public static HookOutcome takeStashed() {
		HookOutcome ret = STASH.get();
		STASH.remove();
		return ret != null ? ret : PROCEED_OUTCOME;
	}

	@Override
	public String toString() {
		return "outcome(" + discriminant + ", " + valueKind + ")";
	}
}
