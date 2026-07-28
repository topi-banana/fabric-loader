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

package net.fabricmc.loader.impl.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.util.LoaderUtil;
import net.fabricmc.loader.impl.wasm.WasmConstants;

/**
 * Checks that a {@code .wasm} goes through the ordinary discovery pipeline: the metadata embedded
 * in its custom section is parsed by the normal parser, and the resulting candidate reports the
 * {@code .wasm} itself as its origin.
 */
public class WasmDiscoveryTest {
	private static final Path FIXTURE = Paths.get("src/test/resources/testing/wasm/minimalComponent.wasm");

	@TempDir
	Path modsDir;

	private FabricLoaderImpl loader;
	private MockedConstruction<FabricLoaderImpl> loaderConstruction;

	/**
	 * The launcher is a process wide singleton that refuses to be replaced, so it is installed once
	 * and only if no other test got there first.
	 */
	@BeforeAll
	static void installLauncher() {
		if (FabricLauncherBase.getLauncher() != null) return;

		FabricLauncher launcher = mock();
		when(launcher.getEnvironmentType()).thenReturn(EnvType.CLIENT);
		when(launcher.isDevelopment()).thenReturn(false);
		FabricLauncherBase.setLauncher(launcher);
	}

	@BeforeEach
	public void setUp() {
		GameProvider provider = mock();
		when(provider.getBuiltinMods()).thenReturn(Collections.emptyList());

		loader = mock();
		when(loader.getGameProvider()).thenReturn(provider);
		when(loader.isDevelopmentEnvironment()).thenReturn(false);

		loaderConstruction = Mockito.mockConstructionWithAnswer(FabricLoaderImpl.class, invocation -> loader);
	}

	@AfterEach
	public void tearDown() {
		loaderConstruction.close();
	}

	@Test
	@DisplayName("A .wasm in the mods folder becomes a mod candidate")
	void discoversWasmMod() throws ModResolutionException {
		ModCandidateImpl candidate = discover(FIXTURE);

		assertNotNull(candidate, "the .wasm was not accepted as a mod");
		assertEquals("wasmtestmod", candidate.getId());
		assertEquals("1.0.0", candidate.getVersion().getFriendlyString());
		assertEquals("WASM Test Mod", candidate.getMetadata().getName());
	}

	@Test
	@DisplayName("The candidate keeps the .wasm as its origin, so errors point at the real file")
	void reportsWasmAsOrigin() throws ModResolutionException {
		ModCandidateImpl candidate = discover(FIXTURE);

		assertEquals(1, candidate.getOriginPaths().size());
		assertTrue(candidate.getOriginPaths().get(0).toString().endsWith(WasmConstants.WASM_EXTENSION),
				candidate.getOriginPaths().toString());
	}

	@Test
	@DisplayName("A .wasm is never marked for remapping")
	void neverRequiresRemap() throws ModResolutionException {
		// the finder passes false, but so would ArgumentModCandidateFinder if it ever accepted .wasm;
		// computeWasmFile hardcodes false so that RuntimeModRemapper cannot try to open it as a zip
		assertEquals(false, discover(FIXTURE).getRequiresRemap());
	}

	@Test
	@DisplayName("A mod declaring no hooks declares no Mixin configuration either")
	void noHooksMeansNoMixinConfig() throws ModResolutionException {
		// the fixture declares an empty hooks array; asking for a config that will never be written
		// would make FabricMixinBootstrap fail on Mixins.addConfiguration
		ModCandidateImpl candidate = discover(FIXTURE);

		assertTrue(candidate.getMetadata().getMixinConfigs(EnvType.CLIENT).isEmpty());
		assertTrue(candidate.getMetadata().getMixinConfigs(EnvType.SERVER).isEmpty());
	}

	@Test
	@DisplayName("The fabric:wasm block is readable through the ordinary custom value API")
	void exposesWasmCustomValue() throws ModResolutionException {
		assertNotNull(discover(FIXTURE).getMetadata().getCustomValue(WasmConstants.CUSTOM_KEY));
	}

	@Test
	@DisplayName("A .wasm without embedded metadata is reported like a non-fabric jar, not a crash")
	void reportsMetadataLessWasmAsNonFabric() throws ModResolutionException, IOException {
		// a bare component preamble: valid wasm, no fabric.mod.json custom section
		Path file = modsDir.resolve("notamod.wasm");
		Files.write(file, new byte[] {0x00, 0x61, 0x73, 0x6d, 0x0d, 0x00, 0x01, 0x00});

		ModDiscoverer discoverer = discoverer(file);
		List<ModCandidateImpl> mods = discoverer.discoverMods(loader, new HashMap<String, Set<ModCandidateImpl>>());

		for (ModCandidateImpl mod : mods) {
			assertTrue(mod.isBuiltin(), "unexpected mod discovered: " + mod);
		}

		assertEquals(1, discoverer.getNonFabricMods().size());
		assertEquals(LoaderUtil.normalizePath(file), discoverer.getNonFabricMods().get(0));
	}

	@Test
	@DisplayName("Only .wasm files are picked up, and hidden ones are ignored")
	void filtersFiles() throws IOException {
		Files.write(modsDir.resolve("mod.wasm"), new byte[] {0});
		Files.write(modsDir.resolve(".hidden.wasm"), new byte[] {0});
		Files.write(modsDir.resolve("mod.jar"), new byte[] {0});
		Files.createDirectory(modsDir.resolve("adir.wasm"));

		assertTrue(WasmModCandidateFinder.isValidFile(modsDir.resolve("mod.wasm")));
		assertTrue(!WasmModCandidateFinder.isValidFile(modsDir.resolve(".hidden.wasm")));
		assertTrue(!WasmModCandidateFinder.isValidFile(modsDir.resolve("mod.jar")));
		assertTrue(!WasmModCandidateFinder.isValidFile(modsDir.resolve("adir.wasm")));
	}

	/**
	 * @return the discovered mod that is not one of the built-ins the discoverer always adds
	 */
	private ModCandidateImpl discover(Path file) throws ModResolutionException {
		for (ModCandidateImpl mod : discoverer(file).discoverMods(loader, new HashMap<String, Set<ModCandidateImpl>>())) {
			if (!mod.isBuiltin()) return mod;
		}

		return null;
	}

	private ModDiscoverer discoverer(Path file) {
		ModDiscoverer discoverer = new ModDiscoverer(mock(), mock());
		final Path normalized = LoaderUtil.normalizePath(file);

		discoverer.addCandidateFinder(new ModCandidateFinder() {
			@Override
			public void findCandidates(ModCandidateConsumer out) {
				out.accept(normalized, false);
			}
		});

		return discoverer;
	}
}
