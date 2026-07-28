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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ComponentDecoderTest {
	private static final byte[] METADATA = "{\"schemaVersion\":1,\"id\":\"wasmtest\",\"version\":\"1.0.0\"}"
			.getBytes(WasmBinaries.UTF_8);

	@Test
	@DisplayName("Tells core modules and components apart by their preamble")
	void detectsKind() throws WasmParseException {
		assertEquals(WasmBinaryKind.CORE_MODULE, ComponentBinary.detectKind(WasmBinaries.Builder.coreModule().build()));
		assertEquals(WasmBinaryKind.COMPONENT, ComponentBinary.detectKind(WasmBinaries.Builder.component().build()));
	}

	@Test
	@DisplayName("Rejects input that is not a WebAssembly binary")
	void rejectsNonWasm() {
		assertThrows(WasmParseException.class, () -> ComponentBinary.detectKind(new byte[0]));
		assertThrows(WasmParseException.class, () -> ComponentBinary.detectKind("not wasm at all".getBytes(WasmBinaries.UTF_8)));

		// right magic, unknown version/layer pair
		byte[] future = {0x00, 0x61, 0x73, 0x6d, 0x7f, 0x00, 0x7f, 0x00};
		assertThrows(WasmParseException.class, () -> ComponentBinary.detectKind(future));
	}

	@Test
	@DisplayName("Finds the fabric.mod.json custom section in a component")
	void findsMetadata() throws WasmParseException {
		byte[] component = WasmBinaries.Builder.component()
				.customSection(ComponentDecoder.METADATA_SECTION_NAME, METADATA)
				.section(WasmBinaries.SECTION_COMPONENT_CORE_MODULE, WasmBinaries.minimalCoreModule())
				.build();

		assertArrayEquals(METADATA, ComponentDecoder.findMetadata(component));
	}

	@Test
	@DisplayName("Returns null rather than failing when there is no metadata section")
	void toleratesMissingMetadata() throws WasmParseException {
		byte[] component = WasmBinaries.Builder.component()
				.section(WasmBinaries.SECTION_COMPONENT_CORE_MODULE, WasmBinaries.minimalCoreModule())
				.build();

		assertNull(ComponentDecoder.findMetadata(component));
	}

	@Test
	@DisplayName("Skips unrelated custom sections and unknown section kinds")
	void skipsIrrelevantSections() throws WasmParseException {
		byte[] component = WasmBinaries.Builder.component()
				.customSection("producers", new byte[] {1, 2, 3})
				.section(9, new byte[] {4, 5, 6, 7}) // start section, skipped
				.section(42, new byte[] {8, 9}) // section kind from a future spec revision
				.customSection(ComponentDecoder.METADATA_SECTION_NAME, METADATA)
				.section(WasmBinaries.SECTION_COMPONENT_CORE_MODULE, WasmBinaries.minimalCoreModule())
				.build();

		ComponentImage image = ComponentDecoder.decode(component);
		assertArrayEquals(METADATA, image.getMetadata());
		assertArrayEquals(WasmBinaries.minimalCoreModule(), image.getCoreModule());
	}

	@Test
	@DisplayName("Reads the core module export list")
	void readsCoreExports() throws WasmParseException {
		byte[] component = WasmBinaries.Builder.component()
				.section(WasmBinaries.SECTION_COMPONENT_CORE_MODULE, WasmBinaries.minimalCoreModule())
				.build();

		CoreModuleInfo info = ComponentDecoder.decode(component).getCoreModuleInfo();

		assertTrue(info.exportsMemory());
		assertTrue(info.hasExport("init", CoreModuleInfo.ExportKind.FUNCTION));
		assertTrue(info.hasExport("on-hook", CoreModuleInfo.ExportKind.FUNCTION));
		assertTrue(info.hasExport("memory", CoreModuleInfo.ExportKind.MEMORY));
		assertEquals(Arrays.asList("memory", "init", "on-hook"), new java.util.ArrayList<>(info.getExports().keySet()));
	}

	@Test
	@DisplayName("Refuses a bare core module, pointing at the toolchain")
	void refusesBareCoreModule() {
		WasmParseException e = assertThrows(WasmParseException.class,
				() -> ComponentDecoder.decode(WasmBinaries.minimalCoreModule()));

		assertTrue(e.getMessage().contains("component"), e.getMessage());
	}

	@Test
	@DisplayName("Refuses a component with no core module")
	void refusesEmptyComponent() {
		WasmParseException e = assertThrows(WasmParseException.class,
				() -> ComponentDecoder.decode(WasmBinaries.Builder.component()
						.customSection(ComponentDecoder.METADATA_SECTION_NAME, METADATA)
						.build()));

		assertTrue(e.getMessage().contains("no core module"), e.getMessage());
	}

	@Test
	@DisplayName("Picks the memory-exporting core module when a shim module is present")
	void picksMainCoreModule() throws WasmParseException {
		byte[] shim = WasmBinaries.Builder.coreModule()
				.exportSection("indirect", WasmBinaries.KIND_FUNCTION)
				.build();

		byte[] component = WasmBinaries.Builder.component()
				.section(WasmBinaries.SECTION_COMPONENT_CORE_MODULE, shim)
				.section(WasmBinaries.SECTION_COMPONENT_CORE_MODULE, WasmBinaries.minimalCoreModule())
				.build();

		assertArrayEquals(WasmBinaries.minimalCoreModule(), ComponentDecoder.decode(component).getCoreModule());
	}

	@Test
	@DisplayName("Refuses a composed component where the mod's own module is ambiguous")
	void refusesAmbiguousComposition() {
		byte[] component = WasmBinaries.Builder.component()
				.section(WasmBinaries.SECTION_COMPONENT_CORE_MODULE, WasmBinaries.minimalCoreModule())
				.section(WasmBinaries.SECTION_COMPONENT_CORE_MODULE, WasmBinaries.minimalCoreModule())
				.build();

		WasmParseException e = assertThrows(WasmParseException.class, () -> ComponentDecoder.decode(component));
		assertTrue(e.getMessage().contains("more than one exports a memory"), e.getMessage());
	}

	@Test
	@DisplayName("Reports truncated input instead of reading out of bounds")
	void reportsTruncation() {
		byte[] component = WasmBinaries.Builder.component()
				.section(WasmBinaries.SECTION_COMPONENT_CORE_MODULE, WasmBinaries.minimalCoreModule())
				.build();

		// lop off the tail so the last section's declared length overruns the buffer
		byte[] truncated = Arrays.copyOf(component, component.length - 4);

		WasmParseException e = assertThrows(WasmParseException.class, () -> ComponentDecoder.findMetadata(truncated));
		assertTrue(e.getMessage().contains("truncated"), e.getMessage());
	}

	@Test
	@DisplayName("Decodes multi-byte LEB128 lengths")
	void decodesLargeSections() throws WasmParseException {
		byte[] payload = new byte[300]; // length needs two LEB128 bytes
		Arrays.fill(payload, (byte) 0x5a);

		byte[] component = WasmBinaries.Builder.component()
				.customSection(ComponentDecoder.METADATA_SECTION_NAME, payload)
				.build();

		assertArrayEquals(payload, ComponentDecoder.findMetadata(component));
	}

	@Test
	@DisplayName("Rejects a malformed LEB128 run rather than looping")
	void rejectsMalformedLeb128() {
		// five continuation bytes in a row is longer than any valid u32 encoding
		byte[] reader = {(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80};
		WasmBinaryReader r = new WasmBinaryReader(reader);

		assertThrows(WasmParseException.class, r::readU32);
	}

	@Test
	@DisplayName("Also finds metadata embedded directly in a core module")
	void findsMetadataInCoreModule() throws WasmParseException {
		byte[] module = WasmBinaries.Builder.coreModule()
				.customSection(ComponentDecoder.METADATA_SECTION_NAME, METADATA)
				.exportSection("memory", WasmBinaries.KIND_MEMORY)
				.build();

		assertNotNull(ComponentDecoder.findMetadata(module));
		assertArrayEquals(METADATA, ComponentDecoder.findMetadata(module));
	}
}
