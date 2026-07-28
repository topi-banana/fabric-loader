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

/**
 * The Mixin injector a hook declaration maps onto.
 */
public enum HookKind {
	INJECT("inject"),
	MODIFY_VARIABLE("modifyVariable"),
	REDIRECT("redirect"),
	MODIFY_ARG("modifyArg");

	private final String declaredName;

	HookKind(String declaredName) {
		this.declaredName = declaredName;
	}

	/**
	 * @return the value used for the {@code kind} field in {@code fabric.mod.json}
	 */
	public String getDeclaredName() {
		return declaredName;
	}

	/**
	 * @return the kind declared as {@code name}, or null if unknown
	 */
	public static HookKind byDeclaredName(String name) {
		for (HookKind kind : values()) {
			if (kind.declaredName.equals(name)) return kind;
		}

		return null;
	}

	/**
	 * @return a human readable list of the accepted {@code kind} values, for error messages
	 */
	public static String listDeclaredNames() {
		StringBuilder sb = new StringBuilder();

		for (HookKind kind : values()) {
			if (sb.length() > 0) sb.append(", ");
			sb.append(kind.declaredName);
		}

		return sb.toString();
	}
}
