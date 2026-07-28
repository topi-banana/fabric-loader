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

package net.fabricmc.loader.impl.wasm.codegen;

import net.fabricmc.loader.api.MappingResolver;

/**
 * Resolves the names in a hook declaration to the namespace the game is actually running in.
 *
 * <p>Hook declarations are written in intermediary, the namespace mods are distributed against.
 * Resolving them here rather than leaving it to Mixin means the generated annotations can carry
 * {@code remap = false} and need no reference map: in production the runtime namespace is
 * intermediary and every mapping is the identity, while in a development environment the names are
 * resolved to the deobfuscated ones up front. The alternative -- emitting intermediary names with
 * {@code remap = true} and no reference map -- would depend on Mixin's fallback behaviour and log
 * warnings for every configuration.
 */
public final class NamespaceMapper {
	/**
	 * The namespace hook declarations are written in.
	 */
	public static final String SOURCE_NAMESPACE = "intermediary";

	private final MappingResolver resolver;
	private final boolean identity;

	public NamespaceMapper(MappingResolver resolver) {
		this.resolver = resolver;
		this.identity = SOURCE_NAMESPACE.equals(resolver.getCurrentRuntimeNamespace());
	}

	/**
	 * @return true if the runtime namespace is the one declarations are written in, so that every
	 *     mapping is the identity; used to keep the generated artifact cache key meaningful
	 */
	public boolean isIdentity() {
		return identity;
	}

	public String getRuntimeNamespace() {
		return resolver.getCurrentRuntimeNamespace();
	}

	/**
	 * @param internalName an internal class name using {@code /} separators
	 * @return the runtime internal name
	 */
	public String mapClass(String internalName) {
		if (identity) return internalName;

		return resolver.mapClassName(SOURCE_NAMESPACE, internalName.replace('/', '.')).replace('.', '/');
	}

	public String mapMethodName(String ownerInternalName, String name, String descriptor) {
		if (identity) return name;

		return resolver.mapMethodName(SOURCE_NAMESPACE, ownerInternalName.replace('/', '.'), name, descriptor);
	}

	public String mapFieldName(String ownerInternalName, String name, String descriptor) {
		if (identity) return name;

		return resolver.mapFieldName(SOURCE_NAMESPACE, ownerInternalName.replace('/', '.'), name, descriptor);
	}

	/**
	 * Rewrites every object type inside a field or method descriptor.
	 *
	 * <p>{@link MappingResolver} has no descriptor aware entry point, so the walk is done here.
	 */
	public String mapDescriptor(String descriptor) {
		if (identity) return descriptor;

		StringBuilder sb = new StringBuilder(descriptor.length());
		int i = 0;

		while (i < descriptor.length()) {
			char c = descriptor.charAt(i);

			if (c != 'L') {
				sb.append(c);
				i++;
				continue;
			}

			int end = descriptor.indexOf(';', i);

			if (end < 0) { // malformed; leave the remainder alone rather than losing it
				sb.append(descriptor, i, descriptor.length());
				break;
			}

			sb.append('L').append(mapClass(descriptor.substring(i + 1, end))).append(';');
			i = end + 1;
		}

		return sb.toString();
	}

	/**
	 * Maps a member selector of the form {@code Lowner;name(args)ret}.
	 */
	public MemberRef mapMember(MemberRef ref) {
		if (identity) return ref;

		return new MemberRef(mapClass(ref.getOwner()),
				mapMethodName(ref.getOwner(), ref.getName(), ref.getDescriptor()),
				mapDescriptor(ref.getDescriptor()));
	}
}
