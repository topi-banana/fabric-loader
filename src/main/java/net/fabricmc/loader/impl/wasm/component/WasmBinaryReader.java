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

package net.fabricmc.loader.impl.wasm.component;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

/**
 * Cursor over a WebAssembly binary providing the primitive decoders that the core module and
 * the component binary formats share.
 *
 * <p>This exists so that mod discovery can read {@code .wasm} files without touching the
 * WebAssembly engine, which targets a newer class file version than the loader itself.
 */
public final class WasmBinaryReader {
	private final byte[] data;
	private final int end;
	private int pos;

	public WasmBinaryReader(byte[] data) {
		this(data, 0, data.length);
	}

	public WasmBinaryReader(byte[] data, int offset, int length) {
		if (offset < 0 || length < 0 || offset + length > data.length) {
			throw new IndexOutOfBoundsException("offset " + offset + " length " + length + " for array of " + data.length);
		}

		this.data = data;
		this.pos = offset;
		this.end = offset + length;
	}

	public int position() {
		return pos;
	}

	public int remaining() {
		return end - pos;
	}

	public boolean hasRemaining() {
		return pos < end;
	}

	public int readU8() throws WasmParseException {
		if (pos >= end) throw new WasmParseException("unexpected end of input at offset " + pos);

		return data[pos++] & 0xff;
	}

	/**
	 * Reads a fixed width little endian unsigned 16 bit integer.
	 */
	public int readU16Le() throws WasmParseException {
		int lo = readU8();
		int hi = readU8();
		return lo | (hi << 8);
	}

	/**
	 * Reads an unsigned LEB128 encoded 32 bit integer.
	 */
	public int readU32() throws WasmParseException {
		int result = 0;
		int shift = 0;

		// a u32 never needs more than 5 LEB128 bytes
		for (int i = 0; i < 5; i++) {
			int b = readU8();
			result |= (b & 0x7f) << shift;

			if ((b & 0x80) == 0) return result;

			shift += 7;
		}

		throw new WasmParseException("malformed LEB128 u32 ending at offset " + pos);
	}

	/**
	 * Reads a length prefixed UTF-8 {@code name} as defined by the WebAssembly binary format.
	 */
	public String readName() throws WasmParseException {
		int length = readU32();
		byte[] bytes = readBytes(length);

		try {
			return new String(bytes, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new WasmParseException("UTF-8 is unavailable", e); // cannot happen on any conformant JVM
		}
	}

	public byte[] readBytes(int length) throws WasmParseException {
		checkAvailable(length);
		byte[] ret = Arrays.copyOfRange(data, pos, pos + length);
		pos += length;
		return ret;
	}

	public void skip(int length) throws WasmParseException {
		checkAvailable(length);
		pos += length;
	}

	private void checkAvailable(int length) throws WasmParseException {
		if (length < 0) throw new WasmParseException("negative length " + length + " at offset " + pos);

		if (length > remaining()) {
			throw new WasmParseException(String.format("truncated input: wanted %d bytes at offset %d, only %d remain", length, pos, remaining()));
		}
	}
}
