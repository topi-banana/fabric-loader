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

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import net.fabricmc.loader.impl.FormattedException;
import net.fabricmc.loader.impl.util.LoaderUtil;
import net.fabricmc.loader.impl.util.SystemProperties;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.fabricmc.loader.impl.wasm.WasmConstants;
import net.fabricmc.loader.impl.wasm.WasmSupport;

/**
 * Finds WebAssembly mods in the mods directory.
 *
 * <p>Kept separate from {@link DirectoryModCandidateFinder} so that the jar path stays untouched,
 * and because a {@code .wasm} never needs remapping: the generator resolves every name to the
 * runtime namespace when it emits the Mixin classes.
 */
public class WasmModCandidateFinder implements ModCandidateFinder {
	private final Path path;

	public WasmModCandidateFinder(Path path) {
		this.path = LoaderUtil.normalizePath(path);
	}

	@Override
	public void findCandidates(ModCandidateConsumer out) {
		if (WasmSupport.isDisabled() || !Files.isDirectory(path)) {
			// the jar finder creates the directory if it is missing, so there is nothing to do here
			return;
		}

		List<Path> found = new ArrayList<>();

		try {
			Files.walkFileTree(path, EnumSet.of(FileVisitOption.FOLLOW_LINKS), 1, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					if (isValidFile(file)) found.add(file);

					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			throw new RuntimeException("Exception while searching for WebAssembly mods in '" + path + "'!", e);
		}

		if (found.isEmpty()) return;

		checkJavaVersion(found);
		Log.debug(LogCategory.DISCOVERY, "Found %d WebAssembly mod candidate(s) in %s", found.size(), path);

		for (Path file : found) {
			out.accept(file, false);
		}
	}

	/**
	 * Fails the launch with an actionable message rather than letting the mods load and blow up on
	 * the first hook, which would happen much later and far from the cause.
	 */
	private static void checkJavaVersion(List<Path> found) {
		if (WasmSupport.isJavaVersionSufficient()) return;

		StringBuilder files = new StringBuilder();

		for (Path file : found) {
			files.append(System.lineSeparator()).append(" - ").append(file.getFileName());
		}

		throw FormattedException.ofLocalized("exception.wasm.javaVersion", String.format(
				"WebAssembly mods require Java %d or later, but the game is running on Java %d:%s%n%n"
				+ "Update Java, remove those files from the mods folder, or pass -D%s=true to ignore them.",
				WasmConstants.MIN_JAVA_VERSION, WasmSupport.currentJavaVersion(), files,
				SystemProperties.WASM_DISABLED));
	}

	/**
	 * Mirrors {@link DirectoryModCandidateFinder#isValidFile(Path)}, but for {@code .wasm}.
	 */
	static boolean isValidFile(Path path) {
		if (!Files.isRegularFile(path)) return false;

		try {
			if (Files.isHidden(path)) return false;
		} catch (IOException e) {
			Log.warn(LogCategory.DISCOVERY, "Error checking if file %s is hidden", path, e);
			return false;
		}

		String fileName = path.getFileName().toString();
		return fileName.endsWith(WasmConstants.WASM_EXTENSION) && !fileName.startsWith(".");
	}
}
