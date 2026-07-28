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

package net.fabricmc.minecraft.test.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.minecraft.test.WasmHookTarget;

/**
 * Exercises WebAssembly mod support on a real, running loader.
 *
 * <p>The whole chain is covered here and nowhere else: a component in the mods folder is discovered,
 * its embedded metadata parsed, Mixin classes generated from its hook declarations, those classes
 * registered with Mixin and applied to a target class, and the resulting handler entered, which
 * instantiates the guest and marshals a call into it.
 *
 * <p>The target is an ordinary class rather than a Minecraft one on purpose, so that a failure here
 * points at the WebAssembly machinery instead of at name mapping.
 */
public class WasmModTest {
	private static final String MOD_ID = "wasmhooktest";

	@Test
	@DisplayName("A .wasm in the mods folder is loaded as a mod")
	public void loadsWasmMod() {
		ModContainer mod = FabricLoader.getInstance().getModContainer(MOD_ID)
				.orElseThrow(() -> new AssertionError("the wasm mod was not loaded; loaded mods: " + modIds()));

		assertEquals("1.0.0", mod.getMetadata().getVersion().getFriendlyString());
		assertEquals("WASM Hook Test Mod", mod.getMetadata().getName());

		// the origin still names the .wasm, not the generated cache directory
		assertTrue(mod.getOrigin().getPaths().get(0).toString().endsWith(".wasm"),
				mod.getOrigin().getPaths().toString());
	}

	@Test
	@DisplayName("The generated artifacts are the mod's code source")
	public void generatedArtifactsAreOnTheClassPath() {
		// Mixins.getConfigs() is not usable for this: Mixin empties it once the configurations have
		// been prepared, so it is already empty by the time any test runs
		ModContainer mod = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow(AssertionError::new);

		assertTrue(mod.findPath(MOD_ID + ".wasm.mixins.json").isPresent(),
				"the generated Mixin configuration is not reachable through the mod");
		assertTrue(mod.findPath("net/fabricmc/wasmgen/" + MOD_ID + "/Mixin_WasmHookTarget_e6e510d1.class").isPresent(),
				"the generated Mixin class is not reachable through the mod");
	}

	@Test
	@DisplayName("A hook cancels the target method and substitutes the guest's return value")
	public void appliesGeneratedInject() {
		// the guest answers hook 0 with return-value(42); without the hook this returns 1
		assertEquals(42, new WasmHookTarget().compute());
	}

	@Test
	@DisplayName("The guest sees the receiver and every parameter of the injected method")
	public void passesArgumentsToGuest() {
		// the guest answers hook 1 with return-value(argument count * 100); the arguments are the
		// receiver plus the two ints, so 3 * 100
		assertEquals(300, new WasmHookTarget().add(1, 2));
	}

	private static List<String> modIds() {
		List<String> ret = new ArrayList<>();

		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			ret.add(mod.getMetadata().getId());
		}

		return ret;
	}
}
