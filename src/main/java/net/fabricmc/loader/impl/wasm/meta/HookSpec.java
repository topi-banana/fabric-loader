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

package net.fabricmc.loader.impl.wasm.meta;

import net.fabricmc.loader.api.metadata.ModEnvironment;

/**
 * One declared hook: where to inject, and which guest dispatch id the generated handler passes.
 *
 * <p>All names are in the namespace the mod was authored against (intermediary); the generator
 * resolves them to the runtime namespace.
 */
public final class HookSpec {
	/**
	 * Used for {@code index} when the declaration leaves it out.
	 */
	public static final int NO_INDEX = -1;

	private final int hookId;
	private final String id;
	private final HookKind kind;
	private final ModEnvironment environment;
	private final String targetClass;
	private final String targetMethod;
	private final String targetDescriptor;
	private final AtSpec at;
	private final int index;
	private final boolean cancellable;
	private final String valueType;
	private final int require;
	private final int priority;
	private final boolean staticTarget;
	private final boolean staticCallee;

	HookSpec(int hookId, String id, HookKind kind, ModEnvironment environment, String targetClass,
			String targetMethod, String targetDescriptor, AtSpec at, int index, boolean cancellable,
			String valueType, int require, int priority, boolean staticTarget, boolean staticCallee) {
		this.staticTarget = staticTarget;
		this.staticCallee = staticCallee;
		this.hookId = hookId;
		this.id = id;
		this.kind = kind;
		this.environment = environment;
		this.targetClass = targetClass;
		this.targetMethod = targetMethod;
		this.targetDescriptor = targetDescriptor;
		this.at = at;
		this.index = index;
		this.cancellable = cancellable;
		this.valueType = valueType;
		this.require = require;
		this.priority = priority;
	}

	/**
	 * Returns a copy with the target names replaced by their runtime namespace equivalents.
	 *
	 * <p>Everything else -- kind, index, flags -- is namespace independent and carried over.
	 */
	public HookSpec withResolvedNames(String targetClass, String targetMethod, String targetDescriptor,
			String atTarget, String valueType) {
		AtSpec resolvedAt = new AtSpec(at.getValue(), atTarget, at.getOrdinal());

		return new HookSpec(hookId, id, kind, environment, targetClass, targetMethod, targetDescriptor,
				resolvedAt, index, cancellable, valueType, require, priority, staticTarget, staticCallee);
	}

	/**
	 * @return the index of this declaration, which is the id the guest's dispatch export receives
	 */
	public int getHookId() {
		return hookId;
	}

	/**
	 * @return the author supplied name, used in log and error messages only
	 */
	public String getId() {
		return id;
	}

	public HookKind getKind() {
		return kind;
	}

	public ModEnvironment getEnvironment() {
		return environment;
	}

	/**
	 * @return internal name of the class to inject into, for example {@code net/minecraft/class_310}
	 */
	public String getTargetClass() {
		return targetClass;
	}

	public String getTargetMethod() {
		return targetMethod;
	}

	public String getTargetDescriptor() {
		return targetDescriptor;
	}

	/**
	 * @return the target as Mixin's {@code method} selector expects it
	 */
	public String getTargetSelector() {
		return targetMethod + targetDescriptor;
	}

	public AtSpec getAt() {
		return at;
	}

	/**
	 * @return the local variable slot for {@code modifyVariable}, or the argument position for
	 *     {@code modifyArg}; {@link #NO_INDEX} if unset
	 */
	public int getIndex() {
		return index;
	}

	public boolean isCancellable() {
		return cancellable;
	}

	/**
	 * @return the JVM descriptor of the value being modified, or null if not applicable
	 */
	public String getValueType() {
		return valueType;
	}

	public int getRequire() {
		return require;
	}

	public int getPriority() {
		return priority;
	}

	/**
	 * Whether the injected method is static.
	 *
	 * <p>Declared rather than derived: the target's bytecode cannot be read while the glue is
	 * generated, because the game jar is only unlocked after Mixin has been bootstrapped. Mixin
	 * requires the handler's static-ness to match the target's, so getting this wrong is an error
	 * when the target class is first loaded.
	 */
	public boolean isStaticTarget() {
		return staticTarget;
	}

	/**
	 * Whether the call a {@code redirect} hook intercepts is static, which decides whether the
	 * handler receives the receiver as its first parameter.
	 */
	public boolean isStaticCallee() {
		return staticCallee;
	}

	@Override
	public String toString() {
		return String.format("hook %d (%s) %s %s.%s%s %s", hookId, id, kind.getDeclaredName(),
				targetClass, targetMethod, targetDescriptor, at);
	}
}
