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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;

/**
 * Builds WebAssembly binaries byte by byte so that the decoder can be tested without a
 * WebAssembly toolchain on the build machine.
 */
final class WasmBinaries {
	static final Charset UTF_8 = Charset.forName("UTF-8");

	static final int SECTION_CUSTOM = 0;
	static final int SECTION_COMPONENT_CORE_MODULE = 1;
	static final int SECTION_CORE_EXPORT = 7;

	static final int KIND_FUNCTION = 0;
	static final int KIND_MEMORY = 2;

	private WasmBinaries() { }

	/**
	 * Builder for a sequence of sections behind a preamble.
	 */
	static final class Builder {
		private final ByteArrayOutputStream out = new ByteArrayOutputStream();

		private Builder(byte[] preamble) {
			write(preamble);
		}

		static Builder coreModule() {
			return new Builder(new byte[] {0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00});
		}

		static Builder component() {
			return new Builder(new byte[] {0x00, 0x61, 0x73, 0x6d, 0x0d, 0x00, 0x01, 0x00});
		}

		Builder section(int id, byte[] content) {
			out.write(id);
			write(u32(content.length));
			write(content);
			return this;
		}

		Builder customSection(String name, byte[] payload) {
			ByteArrayOutputStream content = new ByteArrayOutputStream();
			byte[] nameBytes = name.getBytes(UTF_8);
			writeTo(content, u32(nameBytes.length));
			writeTo(content, nameBytes);
			writeTo(content, payload);
			return section(SECTION_CUSTOM, content.toByteArray());
		}

		/**
		 * Adds a core module export section. {@code entries} alternates name and kind tag.
		 */
		Builder exportSection(Object... nameThenKind) {
			if (nameThenKind.length % 2 != 0) throw new IllegalArgumentException("expected name/kind pairs");

			ByteArrayOutputStream content = new ByteArrayOutputStream();
			writeTo(content, u32(nameThenKind.length / 2));

			for (int i = 0; i < nameThenKind.length; i += 2) {
				byte[] nameBytes = ((String) nameThenKind[i]).getBytes(UTF_8);
				writeTo(content, u32(nameBytes.length));
				writeTo(content, nameBytes);
				content.write((Integer) nameThenKind[i + 1]);
				writeTo(content, u32(0)); // index
			}

			return section(SECTION_CORE_EXPORT, content.toByteArray());
		}

		byte[] build() {
			return out.toByteArray();
		}

		private void write(byte[] bytes) {
			writeTo(out, bytes);
		}
	}

	/**
	 * Encodes an unsigned LEB128 integer.
	 */
	static byte[] u32(int value) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		int remaining = value;

		do {
			int b = remaining & 0x7f;
			remaining >>>= 7;
			if (remaining != 0) b |= 0x80;
			out.write(b);
		} while (remaining != 0);

		return out.toByteArray();
	}

	/**
	 * A minimal core module exporting a memory and the two functions the loader calls.
	 */
	static byte[] minimalCoreModule() {
		return Builder.coreModule()
				.exportSection("memory", KIND_MEMORY, "init", KIND_FUNCTION, "on-hook", KIND_FUNCTION)
				.build();
	}

	private static void writeTo(ByteArrayOutputStream out, byte[] bytes) {
		try {
			out.write(bytes);
		} catch (IOException e) {
			throw new UncheckedIOException(e); // ByteArrayOutputStream does not throw
		}
	}
}
