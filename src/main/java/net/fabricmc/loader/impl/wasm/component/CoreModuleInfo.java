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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The export list of a core WebAssembly module.
 *
 * <p>Only the export section is decoded. Discovery needs it for two things: telling the main core
 * module of a component apart from adapter/shim modules (the main one exports {@code memory}), and
 * checking up front that the exports the generated mixins will call actually exist.
 */
public final class CoreModuleInfo {
	/**
	 * Kind tags used by the core module export section.
	 */
	public enum ExportKind {
		FUNCTION,
		TABLE,
		MEMORY,
		GLOBAL,
		TAG
	}

	private static final int SECTION_EXPORT = 7;

	private static final ExportKind[] EXPORT_KINDS = {
			ExportKind.FUNCTION, ExportKind.TABLE, ExportKind.MEMORY, ExportKind.GLOBAL, ExportKind.TAG
	};

	private final Map<String, ExportKind> exports;

	private CoreModuleInfo(Map<String, ExportKind> exports) {
		this.exports = Collections.unmodifiableMap(exports);
	}

	/**
	 * Decodes the export section of a core module.
	 *
	 * @param data a complete core module binary
	 * @throws WasmParseException if {@code data} is not a core module or its export section is malformed
	 */
	public static CoreModuleInfo read(byte[] data) throws WasmParseException {
		WasmBinaryKind kind = ComponentBinary.detectKind(data);

		if (kind != WasmBinaryKind.CORE_MODULE) {
			throw new WasmParseException("expected a core module, got a " + kind);
		}

		final Map<String, ExportKind> exports = new LinkedHashMap<>();

		ComponentBinary.forEachSection(data, new ComponentBinary.SectionConsumer() {
			@Override
			public void accept(int id, byte[] sectionData, int offset, int length) throws WasmParseException {
				if (id != SECTION_EXPORT) return;

				WasmBinaryReader reader = new WasmBinaryReader(sectionData, offset, length);
				int count = reader.readU32();

				for (int i = 0; i < count; i++) {
					String name = reader.readName();
					int kindTag = reader.readU8();

					if (kindTag >= EXPORT_KINDS.length) {
						throw new WasmParseException(String.format("unknown export kind 0x%02x for export '%s'", kindTag, name));
					}

					reader.readU32(); // index, unused
					exports.put(name, EXPORT_KINDS[kindTag]);
				}
			}
		});

		return new CoreModuleInfo(exports);
	}

	/**
	 * @return the exports in declaration order, mapped to their kind
	 */
	public Map<String, ExportKind> getExports() {
		return exports;
	}

	public boolean hasExport(String name, ExportKind kind) {
		return exports.get(name) == kind;
	}

	public boolean exportsMemory() {
		return exports.containsValue(ExportKind.MEMORY);
	}
}
