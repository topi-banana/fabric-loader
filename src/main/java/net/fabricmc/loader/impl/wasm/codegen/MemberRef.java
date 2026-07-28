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

import net.fabricmc.loader.impl.wasm.meta.WasmMetadataException;

/**
 * A parsed Mixin member selector of the form {@code Lowner;name(args)ret}.
 *
 * <p>Used for the {@code at.target} of {@code redirect} and {@code modifyArg} hooks, where the
 * handler's signature is derived from the call being intercepted rather than from the enclosing
 * method.
 */
public final class MemberRef {
	private final String owner;
	private final String name;
	private final String descriptor;

	public MemberRef(String owner, String name, String descriptor) {
		this.owner = owner;
		this.name = name;
		this.descriptor = descriptor;
	}

	/**
	 * @return internal name of the declaring class
	 */
	public String getOwner() {
		return owner;
	}

	public String getName() {
		return name;
	}

	public String getDescriptor() {
		return descriptor;
	}

	/**
	 * Parses a fully qualified member selector.
	 *
	 * <p>Mixin accepts several looser forms, but the generator requires the fully qualified one so
	 * that the handler signature is unambiguous without reading any bytecode.
	 *
	 * @param target for example {@code Lnet/minecraft/class_5819;method_43048(I)I}
	 */
	public static MemberRef parse(String target, String where, String modId) throws WasmMetadataException {
		int ownerEnd = target.indexOf(';');

		if (!target.startsWith("L") || ownerEnd < 0) {
			throw fail(target, where, modId, "it must start with the owner as \"Lsome/Class;\"");
		}

		int parenStart = target.indexOf('(', ownerEnd);

		if (parenStart < 0 || target.indexOf(')', parenStart) < 0) {
			throw fail(target, where, modId, "it must end with a method descriptor such as \"(I)V\"");
		}

		String owner = target.substring(1, ownerEnd);
		String name = target.substring(ownerEnd + 1, parenStart);
		String descriptor = target.substring(parenStart);

		if (owner.isEmpty() || name.isEmpty()) {
			throw fail(target, where, modId, "the owner and the member name must both be present");
		}

		return new MemberRef(owner, name, descriptor);
	}

	private static WasmMetadataException fail(String target, String where, String modId, String why) {
		return new WasmMetadataException(String.format(
				"mod '%s' %s has the unusable target '%s': %s", modId, where, target, why));
	}

	/**
	 * @return the selector in the form Mixin's {@code @At.target} expects
	 */
	public String toSelector() {
		return "L" + owner + ";" + name + descriptor;
	}

	@Override
	public String toString() {
		return toSelector();
	}
}
