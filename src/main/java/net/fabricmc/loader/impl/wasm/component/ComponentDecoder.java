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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Extracts the loader's inputs from a WebAssembly component binary.
 *
 * <p>Only two of the thirteen component section kinds are read: the custom section carrying
 * {@code fabric.mod.json} and the core module section carrying the code. Everything else is
 * skipped by length, so components using features the loader does not model still yield their
 * metadata instead of failing to parse.
 *
 * <p>This class deliberately has no dependency on the WebAssembly engine, so that mod discovery
 * can run on a JVM too old to load the engine's class files.
 */
public final class ComponentDecoder {
	/**
	 * Name of the custom section that carries the mod metadata.
	 */
	public static final String METADATA_SECTION_NAME = "fabric.mod.json";

	private static final int SECTION_CUSTOM = 0;
	private static final int SECTION_CORE_MODULE = 1;

	private ComponentDecoder() { }

	/**
	 * Reads the embedded {@code fabric.mod.json} without decoding anything else.
	 *
	 * <p>Used during discovery, where most files turn out not to be Fabric mods at all and the
	 * cost of a full decode is not worth paying.
	 *
	 * @param data a component or core module binary
	 * @return the metadata bytes, or null if there is no such custom section
	 */
	public static byte[] findMetadata(byte[] data) throws WasmParseException {
		ComponentBinary.detectKind(data); // validates the preamble
		final byte[][] found = new byte[1][];

		ComponentBinary.forEachSection(data, new ComponentBinary.SectionConsumer() {
			@Override
			public void accept(int id, byte[] sectionData, int offset, int length) throws WasmParseException {
				if (id != SECTION_CUSTOM || found[0] != null) return;

				WasmBinaryReader reader = new WasmBinaryReader(sectionData, offset, length);

				if (METADATA_SECTION_NAME.equals(reader.readName())) {
					found[0] = reader.readBytes(reader.remaining());
				}
			}
		});

		return found[0];
	}

	/**
	 * Fully decodes a component into the pieces the loader needs.
	 *
	 * @param data a component binary; a bare core module is rejected
	 * @throws WasmParseException if {@code data} is not a component, or does not contain exactly
	 *     one core module that could be the mod's code
	 */
	public static ComponentImage decode(byte[] data) throws WasmParseException {
		WasmBinaryKind kind = ComponentBinary.detectKind(data);

		if (kind != WasmBinaryKind.COMPONENT) {
			throw new WasmParseException("expected a WebAssembly component but found a core module; "
					+ "build the mod with a component-aware toolchain (for example 'cargo component build' "
					+ "or 'wasm-tools component new')");
		}

		final byte[][] metadata = new byte[1][];
		final List<byte[]> coreModules = new ArrayList<>();

		ComponentBinary.forEachSection(data, new ComponentBinary.SectionConsumer() {
			@Override
			public void accept(int id, byte[] sectionData, int offset, int length) throws WasmParseException {
				switch (id) {
				case SECTION_CUSTOM:
					WasmBinaryReader reader = new WasmBinaryReader(sectionData, offset, length);

					if (METADATA_SECTION_NAME.equals(reader.readName()) && metadata[0] == null) {
						metadata[0] = reader.readBytes(reader.remaining());
					}

					break;
				case SECTION_CORE_MODULE:
					coreModules.add(Arrays.copyOfRange(sectionData, offset, offset + length));
					break;
				default:
					// every other section kind is irrelevant to the loader
					break;
				}
			}
		});

		byte[] coreModule = selectCoreModule(coreModules);
		return new ComponentImage(metadata[0], coreModule, CoreModuleInfo.read(coreModule));
	}

	/**
	 * Picks the core module that holds the mod's code.
	 *
	 * <p>Components produced by current toolchains normally embed exactly one core module. A shim
	 * or adapter module may appear alongside it; the mod's own module is the one exporting a memory.
	 */
	private static byte[] selectCoreModule(List<byte[]> coreModules) throws WasmParseException {
		if (coreModules.isEmpty()) {
			throw new WasmParseException("component contains no core module, so it has no code to run");
		}

		if (coreModules.size() == 1) {
			return coreModules.get(0);
		}

		byte[] candidate = null;

		for (byte[] module : coreModules) {
			if (CoreModuleInfo.read(module).exportsMemory()) {
				if (candidate != null) {
					throw new WasmParseException(String.format(
							"component embeds %d core modules and more than one exports a memory, so the mod's own "
							+ "module cannot be identified; composed components are not supported", coreModules.size()));
				}

				candidate = module;
			}
		}

		if (candidate == null) {
			throw new WasmParseException(String.format(
					"component embeds %d core modules but none exports a memory, so the mod's own module cannot be "
					+ "identified", coreModules.size()));
		}

		return candidate;
	}
}
