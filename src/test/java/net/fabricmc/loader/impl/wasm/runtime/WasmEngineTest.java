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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.fabricmc.loader.impl.wasm.component.ComponentDecoder;
import net.fabricmc.loader.impl.wasm.component.WasmParseException;

/**
 * Runs the core module of the committed fixture through the engine boundary.
 *
 * <p>This is the test that proves the whole vertical slice: a real component decoded by the
 * loader's own decoder, instantiated by the bundled engine, and called through the boundary
 * interfaces that the Canonical ABI implementation will use.
 */
class WasmEngineTest {
	private static final Path FIXTURE = Paths.get("src/test/resources/testing/wasm/minimalComponent.wasm");

	private static byte[] coreModule;

	@BeforeAll
	static void extractCoreModule() throws IOException, WasmParseException {
		coreModule = ComponentDecoder.decode(Files.readAllBytes(FIXTURE)).getCoreModule();
	}

	@Test
	@DisplayName("The engine adapter loads reflectively and reports itself")
	void loadsEngine() {
		WasmEngine engine = WasmEngineProvider.get();

		assertNotNull(engine);
		assertTrue(engine.describe().startsWith("Chicory"), engine.describe());
		// the provider caches, so repeated use costs nothing
		assertEquals(engine, WasmEngineProvider.get());
	}

	@Test
	@DisplayName("Instantiates the extracted core module and calls its exports")
	void callsExports() {
		WasmInstance instance = instantiate();

		try {
			assertTrue(instance.hasExport("init"));
			assertTrue(instance.hasExport("on-hook"));
			assertFalse(instance.hasExport("no-such-export"));

			assertArrayEquals(new long[0], instance.call("init", new long[0]));
			// the fixture's on-hook ignores its argument and returns 0
			assertArrayEquals(new long[] {0L}, instance.call("on-hook", new long[] {7L}));
		} finally {
			instance.close();
		}
	}

	@Test
	@DisplayName("Linear memory round-trips through the boundary")
	void readsAndWritesMemory() {
		WasmInstance instance = instantiate();

		try {
			assertTrue(instance.memoryByteSize() >= 65536, "expected at least one page");

			instance.writeInt(16, 0x1234abcd);
			assertEquals(0x1234abcd, instance.readInt(16));

			instance.writeLong(32, 0x0123456789abcdefL);
			assertEquals(0x0123456789abcdefL, instance.readLong(32));

			instance.writeByte(64, (byte) 0x5a);
			assertEquals((byte) 0x5a, instance.readByte(64));

			byte[] payload = "fabric".getBytes(java.nio.charset.Charset.forName("UTF-8"));
			instance.writeBytes(128, payload);
			assertArrayEquals(payload, instance.readBytes(128, payload.length));
		} finally {
			instance.close();
		}
	}

	@Test
	@DisplayName("Calling a missing export fails with a message naming it")
	void reportsMissingExport() {
		WasmInstance instance = instantiate();

		try {
			WasmExecutionException e = assertThrows(WasmExecutionException.class,
					() -> instance.call("not-there", new long[0]));
			assertTrue(e.getMessage().contains("not-there"), e.getMessage());
		} finally {
			instance.close();
		}
	}

	@Test
	@DisplayName("An invalid core module is rejected with a wrapped engine error")
	void rejectsInvalidModule() {
		byte[] garbage = {0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00, 0x7f, 0x7f, 0x7f};

		assertThrows(WasmExecutionException.class,
				() -> WasmEngineProvider.get().instantiate(garbage, Collections.<WasmHostFunction>emptyList()));
	}

	@Test
	@DisplayName("Bulk memory operations and memory growth work")
	void runsMemoryStress() throws IOException {
		// keeps proguard honest: the fat jar's shrink pass drops chicory's alternative memory
		// implementation and allocation strategies, so the surviving path has to cover these
		WasmInstance instance = instantiate("memoryStress.wasm");

		try {
			instance.call("init", new long[0]);
			assertEquals(65, instance.call("bulk", new long[0])[0], "memory.fill followed by memory.copy");
			assertEquals(1, instance.call("grow", new long[0])[0], "memory.grow returns the previous page count");
			assertEquals(3 * 65536, instance.memoryByteSize());
		} finally {
			instance.close();
		}
	}

	@Test
	@DisplayName("Integer, float and division opcodes all execute")
	void runsOpcodeStress() throws IOException {
		// chicory's BitOps class is dropped by the shrink pass; this asserts the opcodes it looks
		// like it would serve are in fact handled by code that survives
		WasmInstance instance = instantiate("opcodeStress.wasm");

		try {
			instance.call("init", new long[0]);
			assertEquals(6890612572280610934L, instance.call("bits", new long[0])[0]);
			assertEquals(17.12082869338697d, Double.longBitsToDouble(instance.call("floats", new long[0])[0]), 1e-12);
			assertEquals(-4, (int) instance.call("divs", new long[0])[0]);
		} finally {
			instance.close();
		}
	}

	private static WasmInstance instantiate() {
		return WasmEngineProvider.get().instantiate(coreModule, Collections.<WasmHostFunction>emptyList());
	}

	/**
	 * Instantiates a bare core module fixture, which needs no component decoding.
	 */
	private static WasmInstance instantiate(String fixtureName) throws IOException {
		byte[] module = Files.readAllBytes(FIXTURE.resolveSibling(fixtureName));
		return WasmEngineProvider.get().instantiate(module, Collections.<WasmHostFunction>emptyList());
	}
}
