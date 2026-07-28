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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Exercises the decoder against a component produced by a real toolchain, so that the hand built
 * binaries in {@link ComponentDecoderTest} cannot drift away from what mod authors actually ship.
 */
class RealComponentTest {
	private static final String FIXTURE = "/testing/wasm/minimalComponent.wasm";

	private static byte[] component;

	@BeforeAll
	static void loadFixture() throws IOException {
		try (InputStream is = RealComponentTest.class.getResourceAsStream(FIXTURE)) {
			assertNotNull(is, "missing test fixture " + FIXTURE);

			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[8192];
			int read;

			while ((read = is.read(buf)) != -1) {
				out.write(buf, 0, read);
			}

			component = out.toByteArray();
		}
	}

	@Test
	@DisplayName("Recognises a wasm-tools produced binary as a component")
	void recognisesComponent() throws WasmParseException {
		assertEquals(WasmBinaryKind.COMPONENT, ComponentBinary.detectKind(component));
	}

	@Test
	@DisplayName("Extracts fabric.mod.json past the toolchain's own sections")
	void extractsMetadata() throws WasmParseException {
		byte[] metadata = ComponentDecoder.findMetadata(component);
		assertNotNull(metadata, "no fabric.mod.json custom section found");

		String json = new String(metadata, WasmBinaries.UTF_8);
		assertTrue(json.contains("\"id\":\"wasmtestmod\""), json);
		assertTrue(json.contains("fabric:wasm"), json);
	}

	@Test
	@DisplayName("Finds the single core module and reads its real export section")
	void readsCoreModule() throws WasmParseException {
		ComponentImage image = ComponentDecoder.decode(component);

		assertEquals(WasmBinaryKind.CORE_MODULE, ComponentBinary.detectKind(image.getCoreModule()));

		CoreModuleInfo info = image.getCoreModuleInfo();
		assertTrue(info.exportsMemory());
		assertTrue(info.hasExport("memory", CoreModuleInfo.ExportKind.MEMORY));
		assertTrue(info.hasExport("init", CoreModuleInfo.ExportKind.FUNCTION));
		assertTrue(info.hasExport("on-hook", CoreModuleInfo.ExportKind.FUNCTION));
		assertTrue(info.hasExport("cabi_realloc", CoreModuleInfo.ExportKind.FUNCTION));
	}

	@Test
	@DisplayName("Metadata is byte identical whether or not the whole component is decoded")
	void metadataMatchesFullDecode() throws WasmParseException {
		byte[] viaScan = ComponentDecoder.findMetadata(component);
		byte[] viaDecode = ComponentDecoder.decode(component).getMetadata();

		assertEquals(new String(viaScan, WasmBinaries.UTF_8), new String(viaDecode, WasmBinaries.UTF_8));
	}
}
