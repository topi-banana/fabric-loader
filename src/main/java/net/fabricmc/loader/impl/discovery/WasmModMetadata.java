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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.fabricmc.loader.api.metadata.Person;
import net.fabricmc.loader.impl.metadata.AbstractModMetadata;
import net.fabricmc.loader.impl.metadata.EntrypointMetadata;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.fabricmc.loader.impl.metadata.NestedJarEntry;
import net.fabricmc.loader.impl.wasm.WasmConstants;

/**
 * Metadata of a mod loaded from a {@code .wasm} file.
 *
 * <p>The metadata itself is the ordinary {@code fabric.mod.json} embedded in the component, so it
 * is parsed by the normal parser and merely wrapped here to bend three things:
 *
 * <ul><li>the generated Mixin configuration is added to {@link #getMixinConfigs(EnvType)}, because
 * the config does not exist yet when the metadata is parsed and {@code ModCandidateImpl} holds its
 * metadata in a final field
 * <li>nested jars are dropped, since a {@code .wasm} cannot contain any
 * <li>a class tweaker cannot be honoured, since there is no Java code to tweak against</ul>
 */
final class WasmModMetadata extends AbstractModMetadata implements LoaderModMetadata {
	private final LoaderModMetadata parent;
	private final Collection<String> generatedMixinConfigs;

	/**
	 * @param generatesMixins whether the component declares any hooks, and therefore whether the
	 *     generation stage will emit a Mixin configuration to point at
	 */
	WasmModMetadata(LoaderModMetadata parent, boolean generatesMixins) {
		this.parent = parent;
		this.generatedMixinConfigs = generatesMixins
				? Collections.singletonList(WasmConstants.mixinConfigName(parent.getId()))
				: Collections.<String>emptyList();
	}

	/**
	 * @return the Mixin configuration name the generation stage must write, or null if none
	 */
	String getGeneratedMixinConfig() {
		return generatedMixinConfigs.isEmpty() ? null : generatedMixinConfigs.iterator().next();
	}

	@Override
	public Collection<String> getMixinConfigs(EnvType type) {
		// a .wasm carries no Mixin classes of its own, so the parent's list is expected to be empty;
		// WasmHookMetadata rejects components that declare any, hence no merging is needed here
		return generatedMixinConfigs;
	}

	@Override
	public Collection<NestedJarEntry> getJars() {
		return Collections.emptyList();
	}

	@Override
	public String getClassTweaker() {
		return null;
	}

	@Override
	public String getType() {
		return parent.getType();
	}

	@Override
	public String getId() {
		return parent.getId();
	}

	@Override
	public Collection<String> getProvides() {
		return parent.getProvides();
	}

	@Override
	public Version getVersion() {
		return parent.getVersion();
	}

	@Override
	public void setVersion(Version version) {
		parent.setVersion(version);
	}

	@Override
	public ModEnvironment getEnvironment() {
		return parent.getEnvironment();
	}

	@Override
	public Collection<ModDependency> getDependencies() {
		return parent.getDependencies();
	}

	@Override
	public void setDependencies(Collection<ModDependency> dependencies) {
		parent.setDependencies(dependencies);
	}

	@Override
	public String getName() {
		return parent.getName();
	}

	@Override
	public String getDescription() {
		return parent.getDescription();
	}

	@Override
	public Collection<Person> getAuthors() {
		return parent.getAuthors();
	}

	@Override
	public Collection<Person> getContributors() {
		return parent.getContributors();
	}

	@Override
	public ContactInformation getContact() {
		return parent.getContact();
	}

	@Override
	public Collection<String> getLicense() {
		return parent.getLicense();
	}

	@Override
	public Optional<String> getIconPath(int size) {
		return parent.getIconPath(size);
	}

	@Override
	public boolean containsCustomValue(String key) {
		return parent.containsCustomValue(key);
	}

	@Override
	public CustomValue getCustomValue(String key) {
		return parent.getCustomValue(key);
	}

	@Override
	public Map<String, CustomValue> getCustomValues() {
		return parent.getCustomValues();
	}

	@Override
	public int getSchemaVersion() {
		return parent.getSchemaVersion();
	}

	@Override
	public Map<String, String> getLanguageAdapterDefinitions() {
		return parent.getLanguageAdapterDefinitions();
	}

	@Override
	public boolean loadsInEnvironment(EnvType type) {
		return parent.loadsInEnvironment(type);
	}

	@Override
	public Collection<String> getOldInitializers() {
		return parent.getOldInitializers();
	}

	@Override
	public List<EntrypointMetadata> getEntrypoints(String type) {
		return parent.getEntrypoints(type);
	}

	@Override
	public Collection<String> getEntrypointKeys() {
		return parent.getEntrypointKeys();
	}

	@Override
	public void emitFormatWarnings() {
		parent.emitFormatWarnings();
	}
}
