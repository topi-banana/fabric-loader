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

import java.nio.charset.Charset;

/**
 * The slice of the WebAssembly Canonical ABI the {@code fabric:mod} world needs.
 *
 * <p>Only two shapes ever cross the boundary, which is why this can be hand written rather than
 * generated from the interface types:
 *
 * <ul><li>{@code value} -- a record of a {@code value-kind} discriminant and a {@code u64}. It is
 * fixed size and contains no pointers, which is the whole reason for its shape: {@code list<value>}
 * is then a plain array with a constant stride, instead of the far more expensive
 * {@code list<variant>} it would otherwise be.
 * <li>{@code outcome} -- an eight case variant whose payload is either a {@code value} or a string.</ul>
 *
 * <p>Layout follows the specification's rules: a record's alignment is the maximum of its fields'
 * and each field sits at its own aligned offset; a variant's discriminant is the smallest integer
 * covering the case count, its alignment the maximum of the discriminant's and every payload's, and
 * its payload starts at that aligned offset.
 */
public final class CanonicalAbi {
	static final Charset UTF_8 = Charset.forName("UTF-8");

	/**
	 * {@code record value { kind: value-kind, bits: u64 }}: kind at 0, bits at 8, align 8.
	 */
	public static final int VALUE_ALIGN = 8;
	public static final int VALUE_SIZE = 16;
	private static final int VALUE_KIND_OFFSET = 0;
	private static final int VALUE_BITS_OFFSET = 8;

	/**
	 * {@code variant outcome}: one byte discriminant, payload at 8, largest payload a value.
	 */
	public static final int OUTCOME_ALIGN = 8;
	public static final int OUTCOME_SIZE = 24;
	private static final int OUTCOME_PAYLOAD_OFFSET = 8;

	private CanonicalAbi() { }

	/**
	 * Writes the pushed arguments into linear memory as a {@code list<value>}.
	 *
	 * @param handles used to hand out a handle for every object argument
	 * @return the list's address; the length is {@code args.size()}
	 */
	public static int lowerArgs(WasmInstance instance, GuestAllocator allocator, HookArgs args, HandleTable handles) {
		int count = args.size();
		if (count == 0) return 0; // an empty list needs no allocation, only a null pointer

		int address = allocator.realloc(0, 0, VALUE_ALIGN, count * VALUE_SIZE);

		if (address == 0) {
			throw new WasmExecutionException("guest allocator returned null for " + (count * VALUE_SIZE) + " bytes");
		}

		for (int i = 0; i < count; i++) {
			ValueKind kind = args.kindAt(i);
			long bits;

			if (kind == ValueKind.OBJECT) {
				Object ref = args.refAt(i);
				bits = ref != null ? handles.allocate(ref) & 0xffffffffL : 0L;
			} else {
				bits = args.bitsAt(i);
			}

			writeValue(instance, address + i * VALUE_SIZE, kind, bits);
		}

		return address;
	}

	/**
	 * Writes a single {@code value} record.
	 */
	public static void writeValue(WasmInstance instance, int address, ValueKind kind, long bits) {
		// the value-kind enum has fewer than 256 cases, so its discriminant occupies one byte
		instance.writeByte(address + VALUE_KIND_OFFSET, (byte) (kind != null ? kind.ordinal() : ValueKind.NONE.ordinal()));
		instance.writeLong(address + VALUE_BITS_OFFSET, bits);
	}

	/**
	 * Reads an {@code outcome} variant written by the guest.
	 *
	 * @param address the pointer the guest returned; results wider than one flat value are always
	 *     returned indirectly
	 */
	public static HookOutcome liftOutcome(WasmInstance instance, int address, HandleTable handles) {
		int discriminant = instance.readByte(address) & 0xff;

		switch (discriminant) {
		case HookOutcome.PROCEED:
		case HookOutcome.PASSTHROUGH:
		case HookOutcome.SKIP:
			return HookOutcome.PROCEED_OUTCOME;
		case HookOutcome.CANCEL:
			return HookOutcome.CANCEL_OUTCOME;
		case HookOutcome.RETURN_VALUE:
		case HookOutcome.REPLACE:
		case HookOutcome.SUPPLY:
			return liftValuePayload(instance, address + OUTCOME_PAYLOAD_OFFSET, discriminant, handles);
		case HookOutcome.FAILED:
			throw new WasmExecutionException("guest reported a failure: "
					+ liftString(instance, address + OUTCOME_PAYLOAD_OFFSET));
		default:
			throw new WasmExecutionException("guest returned outcome discriminant " + discriminant
					+ ", which this loader does not implement");
		}
	}

	private static HookOutcome liftValuePayload(WasmInstance instance, int address, int discriminant,
			HandleTable handles) {
		int kindOrdinal = instance.readByte(address + VALUE_KIND_OFFSET) & 0xff;
		ValueKind kind = ValueKind.byDiscriminant(kindOrdinal);

		if (kind == null) {
			throw new WasmExecutionException("guest returned value-kind " + kindOrdinal + ", which does not exist");
		}

		long bits = instance.readLong(address + VALUE_BITS_OFFSET);
		Object ref = null;

		if (kind == ValueKind.OBJECT) {
			ref = handles.resolveOrNull((int) bits);
		}

		return new HookOutcome(discriminant, kind, bits, ref);
	}

	/**
	 * Reads a {@code string}, which the ABI represents as a pointer and a length in bytes.
	 *
	 * <p>Only the {@code utf8} encoding is implemented; the world pins it, so a guest built for a
	 * different one is a build error rather than something to handle here.
	 */
	public static String liftString(WasmInstance instance, int address) {
		int pointer = instance.readInt(address);
		int length = instance.readInt(address + 4);

		if (length < 0 || pointer < 0) {
			throw new WasmExecutionException("guest returned a string at " + pointer + " of length " + length);
		}

		if (length == 0) return "";

		return new String(instance.readBytes(pointer, length), UTF_8);
	}

	/**
	 * Writes a string into linear memory.
	 *
	 * @return the address, with the byte length in the high 32 bits
	 */
	public static long lowerString(WasmInstance instance, GuestAllocator allocator, String value) {
		byte[] bytes = value.getBytes(UTF_8);
		if (bytes.length == 0) return 0L;

		int address = allocator.realloc(0, 0, 1, bytes.length);

		if (address == 0) {
			throw new WasmExecutionException("guest allocator returned null for a " + bytes.length + " byte string");
		}

		instance.writeBytes(address, bytes);
		return (address & 0xffffffffL) | ((long) bytes.length << 32);
	}
}
