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

/**
 * Preamble handling and section framing shared by the core module and component binary formats.
 *
 * <p>Both formats are an 8 byte preamble followed by a sequence of sections, each of which is a
 * one byte id, a LEB128 length and that many content bytes. Only the meaning of the section ids
 * differs, so walking sections is common code.
 */
public final class ComponentBinary {
	/**
	 * The preamble length: 4 magic bytes, a 16 bit version and a 16 bit layer.
	 */
	public static final int PREAMBLE_LENGTH = 8;

	private static final byte[] MAGIC = {0x00, 0x61, 0x73, 0x6d}; // "\0asm"

	private static final int CORE_MODULE_VERSION = 0x0001;
	private static final int CORE_MODULE_LAYER = 0x0000;

	private static final int COMPONENT_VERSION = 0x000d;
	private static final int COMPONENT_LAYER = 0x0001;

	private ComponentBinary() { }

	/**
	 * Receives each top level section of a binary in order.
	 */
	public interface SectionConsumer {
		/**
		 * @param id the section id, interpreted according to the binary's {@link WasmBinaryKind}
		 * @param data the whole binary; the section content is {@code [offset, offset + length)}
		 * @param offset offset of the section content within {@code data}
		 * @param length length of the section content
		 */
		void accept(int id, byte[] data, int offset, int length) throws WasmParseException;
	}

	/**
	 * Determines whether {@code data} is a core module or a component.
	 *
	 * @throws WasmParseException if the magic is absent or the version/layer pair is unknown
	 */
	public static WasmBinaryKind detectKind(byte[] data) throws WasmParseException {
		if (data.length < PREAMBLE_LENGTH) {
			throw new WasmParseException("not a WebAssembly binary: only " + data.length + " bytes, need at least " + PREAMBLE_LENGTH);
		}

		for (int i = 0; i < MAGIC.length; i++) {
			if (data[i] != MAGIC[i]) {
				throw new WasmParseException(String.format("not a WebAssembly binary: bad magic %02x %02x %02x %02x", data[0], data[1], data[2], data[3]));
			}
		}

		WasmBinaryReader reader = new WasmBinaryReader(data, MAGIC.length, PREAMBLE_LENGTH - MAGIC.length);
		int version = reader.readU16Le();
		int layer = reader.readU16Le();

		if (version == CORE_MODULE_VERSION && layer == CORE_MODULE_LAYER) {
			return WasmBinaryKind.CORE_MODULE;
		}

		if (version == COMPONENT_VERSION && layer == COMPONENT_LAYER) {
			return WasmBinaryKind.COMPONENT;
		}

		throw new WasmParseException(String.format("unsupported WebAssembly binary: version 0x%04x layer 0x%04x", version, layer));
	}

	/**
	 * Walks the top level sections of a binary, skipping the preamble.
	 *
	 * <p>The caller is expected to ignore section ids it does not care about; unknown ids are
	 * passed through rather than rejected so that binaries using newer features still yield
	 * their metadata.
	 */
	public static void forEachSection(byte[] data, SectionConsumer consumer) throws WasmParseException {
		WasmBinaryReader reader = new WasmBinaryReader(data, PREAMBLE_LENGTH, data.length - PREAMBLE_LENGTH);

		while (reader.hasRemaining()) {
			int id = reader.readU8();
			int size = reader.readU32();
			int offset = reader.position();

			// bounds-check before handing the range out
			reader.skip(size);

			consumer.accept(id, data, offset, size);
		}
	}
}
