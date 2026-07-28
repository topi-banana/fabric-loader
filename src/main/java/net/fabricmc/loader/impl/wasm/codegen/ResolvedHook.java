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

import net.fabricmc.loader.impl.wasm.meta.HookSpec;
import net.fabricmc.loader.impl.wasm.meta.WasmMetadataException;

/**
 * A hook declaration with every name resolved to the runtime namespace, plus the derived handler
 * signature. This is what the generator consumes.
 */
public final class ResolvedHook {
	private final HookSpec spec;
	private final int globalHookId;
	private final String targetClass;
	private final String targetMethod;
	private final String targetDescriptor;
	private final MemberRef atTarget;
	private final HandlerSignature signature;

	private ResolvedHook(HookSpec spec, int globalHookId, String targetClass, String targetMethod,
			String targetDescriptor, MemberRef atTarget, HandlerSignature signature) {
		this.spec = spec;
		this.globalHookId = globalHookId;
		this.targetClass = targetClass;
		this.targetMethod = targetMethod;
		this.targetDescriptor = targetDescriptor;
		this.atTarget = atTarget;
		this.signature = signature;
	}

	/**
	 * Resolves a declaration.
	 *
	 * @param globalHookId the id the generated handler carries, unique across all mods
	 */
	public static ResolvedHook resolve(HookSpec spec, int globalHookId, NamespaceMapper mapper, String modId)
			throws WasmMetadataException {
		String targetClass = mapper.mapClass(spec.getTargetClass());
		String targetDescriptor = mapper.mapDescriptor(spec.getTargetDescriptor());
		String targetMethod = mapper.mapMethodName(spec.getTargetClass(), spec.getTargetMethod(),
				spec.getTargetDescriptor());

		MemberRef atTarget = null;

		if (spec.getAt().getTarget() != null) {
			atTarget = mapper.mapMember(MemberRef.parse(spec.getAt().getTarget(),
					"hook '" + spec.getId() + "' at.target", modId));
		}

		// the signature is derived from runtime-namespace types so that the emitted descriptor
		// matches what Mixin will compare it against
		HookSpec runtimeSpec = spec.withResolvedNames(targetClass, targetMethod, targetDescriptor,
				atTarget != null ? atTarget.toSelector() : null,
				spec.getValueType() != null ? mapper.mapDescriptor(spec.getValueType()) : null);

		return new ResolvedHook(runtimeSpec, globalHookId, targetClass, targetMethod, targetDescriptor,
				atTarget, HandlerSignature.of(runtimeSpec, modId));
	}

	public HookSpec getSpec() {
		return spec;
	}

	public int getGlobalHookId() {
		return globalHookId;
	}

	/**
	 * @return internal name of the target class in the runtime namespace
	 */
	public String getTargetClass() {
		return targetClass;
	}

	/**
	 * @return the selector for Mixin's {@code method} attribute
	 */
	public String getTargetSelector() {
		return targetMethod + targetDescriptor;
	}

	/**
	 * @return the resolved {@code at.target}, or null if the injection point needs none
	 */
	public MemberRef getAtTarget() {
		return atTarget;
	}

	public HandlerSignature getSignature() {
		return signature;
	}

	/**
	 * @return the generated handler's method name; {@code $} keeps it clear of any target member
	 */
	public String getHandlerName() {
		return "wasm$h" + globalHookId;
	}

	@Override
	public String toString() {
		return String.format("hook %d '%s' (%s) -> %s.%s", globalHookId, spec.getId(),
				spec.getKind().getDeclaredName(), targetClass, getTargetSelector());
	}
}
