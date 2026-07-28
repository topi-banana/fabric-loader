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

package net.fabricmc.loader.impl.wasm;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.FormattedException;
import net.fabricmc.loader.impl.discovery.ModCandidateImpl;
import net.fabricmc.loader.impl.util.ExceptionUtil;
import net.fabricmc.loader.impl.util.SystemProperties;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.fabricmc.loader.impl.wasm.codegen.MixinClassGenerator;
import net.fabricmc.loader.impl.wasm.codegen.MixinConfigGenerator;
import net.fabricmc.loader.impl.wasm.codegen.NamespaceMapper;
import net.fabricmc.loader.impl.wasm.codegen.ResolvedHook;
import net.fabricmc.loader.impl.wasm.component.ComponentDecoder;
import net.fabricmc.loader.impl.wasm.component.ComponentImage;
import net.fabricmc.loader.impl.wasm.component.CoreModuleInfo;
import net.fabricmc.loader.impl.wasm.meta.HookSpec;
import net.fabricmc.loader.impl.wasm.meta.WasmMetadataException;
import net.fabricmc.loader.impl.wasm.meta.WasmModSpec;

/**
 * Turns each discovered {@code .wasm} into an ordinary mod code source.
 *
 * <p>The component is decoded, its core module extracted, and Mixin classes plus a Mixin
 * configuration generated for the hooks it declares. The candidate's paths are then swapped to the
 * generated directory, so that everything downstream -- class path setup, Mixin registration,
 * dependency resolution -- treats the mod exactly like a directory shaped jar mod.
 *
 * <p>Runs after dependency resolution and before {@code addMod}, because {@code ModContainerImpl}
 * captures the candidate's paths in its constructor.
 */
public final class WasmModProcessor {
	/**
	 * Export the guest must provide, called once before any hook fires.
	 */
	public static final String EXPORT_INIT = "init";

	/**
	 * Export every generated Mixin handler dispatches through.
	 */
	public static final String EXPORT_ON_HOOK = "on-hook";

	/**
	 * Subdirectory of the cache entry that becomes the mod's code source.
	 */
	private static final String CLASSES_DIR = "classes";

	private static final String CORE_MODULE_NAME = "module.wasm";
	private static final String STAMP_NAME = ".stamp";

	private WasmModProcessor() { }

	/**
	 * @param candidates all resolved candidates; non-WebAssembly ones are left alone
	 * @param cacheDir the {@code .fabric/wasmMods} directory
	 */
	public static void process(Collection<ModCandidateImpl> candidates, Path cacheDir) {
		for (ModCandidateImpl candidate : candidates) {
			Path origin = wasmPathOf(candidate);
			if (origin == null) continue;

			try {
				process(candidate, origin, cacheDir);
			} catch (Throwable t) {
				throw FormattedException.ofLocalized("exception.wasm.generationFailure",
						String.format("Failed to prepare the WebAssembly mod '%s' from %s: %s",
								candidate.getId(), origin, t), t);
			}
		}

		if (!WasmModRegistry.isEmpty()) {
			Log.info(LogCategory.GENERAL, "Loaded %d WebAssembly mod(s) with %d hook(s)",
					WasmModRegistry.getMods().size(), WasmModRegistry.getHookCount());
		}
	}

	private static void process(ModCandidateImpl candidate, Path origin, Path cacheDir) throws IOException {
		byte[] wasm = Files.readAllBytes(origin);
		String modId = candidate.getId();

		ComponentImage image;
		WasmModSpec spec;

		try {
			image = ComponentDecoder.decode(wasm);
			spec = WasmModSpec.parse(candidate.getMetadata().getCustomValue(WasmConstants.CUSTOM_KEY), modId);
		} catch (Exception e) {
			throw ExceptionUtil.wrap(e);
		}

		checkRequiredExports(image.getCoreModuleInfo(), spec, modId);

		Path modDir = cacheDir.resolve(modId + "-" + shortHash(wasm) + "-" + generatorTag());
		Path classesDir = modDir.resolve(CLASSES_DIR);
		Path coreModulePath = modDir.resolve(CORE_MODULE_NAME);

		if (needsGeneration(modDir)) {
			generate(modDir, classesDir, coreModulePath, image, spec, modId);
		} else {
			Log.debug(LogCategory.GENERAL, "Reusing generated glue for WebAssembly mod %s", modId);
		}

		candidate.setPaths(Collections.singletonList(classesDir));
		WasmModRegistry.register(new WasmModEntry(modId, coreModulePath, origin, spec));
	}

	/**
	 * Writes the cache entry through a temporary directory, so that a half written entry can never
	 * be mistaken for a complete one -- the game directory may be shared between instances.
	 */
	private static void generate(Path modDir, Path classesDir, Path coreModulePath, ComponentImage image,
			WasmModSpec spec, String modId) throws IOException {
		Path tmpDir = modDir.resolveSibling(modDir.getFileName() + ".tmp-" + Long.toHexString(System.nanoTime()));
		deleteRecursively(tmpDir);
		Files.createDirectories(tmpDir.resolve(CLASSES_DIR));

		Files.write(tmpDir.resolve(CORE_MODULE_NAME), image.getCoreModule());
		writeMixins(tmpDir.resolve(CLASSES_DIR), spec, modId);
		Files.write(tmpDir.resolve(STAMP_NAME), generatorTag().getBytes("UTF-8"));

		deleteRecursively(modDir);
		Files.createDirectories(modDir.getParent());
		Files.move(tmpDir, modDir);

		Log.debug(LogCategory.GENERAL, "Generated glue for WebAssembly mod %s in %s", modId, modDir);
	}

	/**
	 * Generates one Mixin class per target class, plus the configuration listing them.
	 *
	 * <p>Hooks are grouped by target so that a class targeting several methods produces one Mixin
	 * rather than several, and by environment so that a client only hook cannot drag its target into
	 * a dedicated server.
	 */
	private static void writeMixins(Path classesDir, WasmModSpec spec, String modId) throws IOException {
		if (spec.getHooks().isEmpty()) return;

		NamespaceMapper mapper = new NamespaceMapper(FabricLoaderImpl.INSTANCE.getMappingResolver());
		MixinClassGenerator generator = new MixinClassGenerator(modId);

		// LinkedHashMap keeps generation deterministic, which keeps the cache reusable
		Map<String, List<ResolvedHook>> byTarget = new LinkedHashMap<>();
		Map<String, ModEnvironment> environments = new LinkedHashMap<>();
		int globalHookId = WasmModRegistry.nextGlobalHookId();

		for (HookSpec hook : spec.getHooks()) {
			ResolvedHook resolved;

			try {
				resolved = ResolvedHook.resolve(hook, globalHookId++, mapper, modId);
			} catch (WasmMetadataException e) {
				throw new IOException(e.getMessage(), e);
			}

			// a target hooked from more than one environment would need two Mixin classes; the
			// environment is therefore part of the grouping key
			String key = resolved.getTargetClass() + "|" + hook.getEnvironment();
			List<ResolvedHook> group = byTarget.get(key);

			if (group == null) {
				group = new ArrayList<>();
				byTarget.put(key, group);
				environments.put(key, hook.getEnvironment());
			}

			group.add(resolved);
		}

		List<String> universal = new ArrayList<>();
		List<String> client = new ArrayList<>();
		List<String> server = new ArrayList<>();

		for (Map.Entry<String, List<ResolvedHook>> entry : byTarget.entrySet()) {
			List<ResolvedHook> hooks = entry.getValue();
			MixinClassGenerator.Generated generated = generator.generate(hooks.get(0).getTargetClass(), hooks);

			Path classFile = classesDir.resolve(generated.getInternalName() + ".class");
			Files.createDirectories(classFile.getParent());
			Files.write(classFile, generated.getBytes());

			switch (environments.get(entry.getKey())) {
			case CLIENT:
				client.add(generated.getSimpleName());
				break;
			case SERVER:
				server.add(generated.getSimpleName());
				break;
			default:
				universal.add(generated.getSimpleName());
				break;
			}

			Log.debug(LogCategory.GENERAL, "Generated %s for %d hook(s) on %s", generated.getInternalName(),
					hooks.size(), hooks.get(0).getTargetClass());
		}

		Files.write(classesDir.resolve(WasmConstants.mixinConfigName(modId)),
				MixinConfigGenerator.write(modId, universal, client, server).getBytes("UTF-8"));
	}

	private static boolean needsGeneration(Path modDir) {
		return SystemProperties.isSet(SystemProperties.DEBUG_WASM_FORCE_REGEN)
				|| !Files.exists(modDir.resolve(STAMP_NAME));
	}

	/**
	 * Warns if the core module does not obviously expose the functions the loader will call.
	 *
	 * <p>Deliberately not fatal. The loader resolves the guest's exports by their component level
	 * names, relying on the convention that a toolchain carries those through to the core module.
	 * That convention holds for what current tooling emits, but it is a convention rather than a
	 * guarantee -- properly resolving the names would mean decoding the component's canonical
	 * function and alias sections, which is out of scope. Failing the launch over a mismatch would
	 * therefore risk rejecting a perfectly good mod, so the check is advisory here and the
	 * authoritative error comes from the engine, which can name what the guest actually exports.
	 */
	private static void checkRequiredExports(CoreModuleInfo info, WasmModSpec spec, String modId) {
		List<String> missing = new ArrayList<>();

		if (!info.hasExport(EXPORT_INIT, CoreModuleInfo.ExportKind.FUNCTION)) missing.add(EXPORT_INIT);

		if (!spec.getHooks().isEmpty() && !info.hasExport(EXPORT_ON_HOOK, CoreModuleInfo.ExportKind.FUNCTION)) {
			missing.add(EXPORT_ON_HOOK);
		}

		if (missing.isEmpty()) return;

		Log.warn(LogCategory.GENERAL, "WebAssembly mod '%s' has no core module export named %s; its exports are %s. "
				+ "If its hooks do not fire, the component's export names were not carried through to its core "
				+ "module and the mod needs rebuilding with a toolchain that does so.",
				modId, missing, info.getExports().keySet());
	}

	/**
	 * Identifies the shape of the generated output, so that a loader or namespace change invalidates
	 * the cache without the user having to clear it.
	 */
	private static String generatorTag() {
		return "g" + WasmConstants.GENERATOR_VERSION;
	}

	private static String shortHash(byte[] data) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
			StringBuilder sb = new StringBuilder(16);

			for (int i = 0; i < 8; i++) {
				sb.append(String.format("%02x", digest[i]));
			}

			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("SHA-256 is unavailable", e); // required of every JVM
		}
	}

	/**
	 * @return the {@code .wasm} a candidate came from, or null if it is not a WebAssembly mod
	 */
	private static Path wasmPathOf(ModCandidateImpl candidate) {
		if (candidate.isBuiltin() || !candidate.hasPath()) return null;

		List<Path> paths = candidate.getPaths();
		if (paths.size() != 1) return null;

		Path path = paths.get(0);
		return WasmSupport.isWasmPath(path) ? path : null;
	}

	private static void deleteRecursively(Path path) throws IOException {
		if (!Files.exists(path)) return;

		Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}
}
