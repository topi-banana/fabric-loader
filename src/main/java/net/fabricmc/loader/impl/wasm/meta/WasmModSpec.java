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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.fabricmc.loader.impl.wasm.WasmConstants;

/**
 * The {@code custom["fabric:wasm"]} block of a WebAssembly mod.
 *
 * <p>Read through the ordinary {@link CustomValue} API, so the surrounding {@code fabric.mod.json}
 * goes through the normal parser and gets the normal {@code id}/{@code version}/dependency handling.
 */
public final class WasmModSpec {
	private final int abiVersion;
	private final boolean reentrant;
	private final List<HookSpec> hooks;

	private WasmModSpec(int abiVersion, boolean reentrant, List<HookSpec> hooks) {
		this.abiVersion = abiVersion;
		this.reentrant = reentrant;
		this.hooks = Collections.unmodifiableList(hooks);
	}

	public int getAbiVersion() {
		return abiVersion;
	}

	/**
	 * @return whether the mod asserts that its bindings tolerate being re-entered
	 */
	public boolean isReentrant() {
		return reentrant;
	}

	public List<HookSpec> getHooks() {
		return hooks;
	}

	/**
	 * Reads and validates the block.
	 *
	 * @param value the value of the {@code fabric:wasm} custom key, or null if absent
	 * @param modId the mod's id, for error messages
	 * @throws WasmMetadataException if the block is absent or does not describe hooks the loader can generate
	 */
	public static WasmModSpec parse(CustomValue value, String modId) throws WasmMetadataException {
		if (value == null) {
			throw new WasmMetadataException(String.format(
					"mod '%s' has no custom[\"%s\"] block, so the loader does not know what it wants to hook",
					modId, WasmConstants.CUSTOM_KEY));
		}

		CustomValue.CvObject root = asObject(value, "custom[\"" + WasmConstants.CUSTOM_KEY + "\"]", modId);
		int abi = requireInt(root, "abi", "custom[\"" + WasmConstants.CUSTOM_KEY + "\"]", modId);

		if (abi != WasmConstants.SUPPORTED_ABI_VERSION) {
			throw new WasmMetadataException(String.format(
					"mod '%s' declares WebAssembly ABI version %d, but this loader implements version %d",
					modId, abi, WasmConstants.SUPPORTED_ABI_VERSION));
		}

		boolean reentrant = optionalBoolean(root, "reentrant", false, modId);
		List<HookSpec> hooks = new ArrayList<>();
		CustomValue hooksValue = root.get("hooks");

		if (hooksValue != null) {
			CustomValue.CvArray array = asArray(hooksValue, "hooks", modId);

			for (int i = 0; i < array.size(); i++) {
				hooks.add(parseHook(array.get(i), i, modId));
			}
		}

		return new WasmModSpec(abi, reentrant, hooks);
	}

	private static HookSpec parseHook(CustomValue value, int hookId, String modId) throws WasmMetadataException {
		String where = "hooks[" + hookId + "]";
		CustomValue.CvObject hook = asObject(value, where, modId);

		String id = optionalString(hook, "id", "hook#" + hookId, where, modId);
		where = where + " ('" + id + "')";

		String declaredKind = requireString(hook, "kind", where, modId);
		HookKind kind = HookKind.byDeclaredName(declaredKind);

		if (kind == null) {
			throw new WasmMetadataException(String.format("mod '%s' %s declares an unknown kind '%s'; expected one of %s",
					modId, where, declaredKind, HookKind.listDeclaredNames()));
		}

		CustomValue.CvObject target = asObject(require(hook, "target", where, modId), where + ".target", modId);
		String targetClass = requireString(target, "class", where + ".target", modId);
		String targetMethod = requireString(target, "method", where + ".target", modId);
		String targetDescriptor = requireString(target, "descriptor", where + ".target", modId);

		checkInternalName(targetClass, where + ".target.class", modId);
		checkMethodDescriptor(targetDescriptor, where + ".target.descriptor", modId);

		AtSpec at = parseAt(asObject(require(hook, "at", where, modId), where + ".at", modId), where + ".at", modId);
		int index = optionalInt(hook, "index", HookSpec.NO_INDEX, where, modId);
		boolean cancellable = optionalBoolean(hook, "cancellable", false, modId);
		String valueType = optionalString(hook, "valueType", null, where, modId);
		ModEnvironment environment = parseEnvironment(optionalString(hook, "env", "*", where, modId), where, modId);
		int require = optionalInt(hook, "require", 1, where, modId);
		int priority = optionalInt(hook, "priority", 1000, where, modId);
		boolean staticTarget = optionalBoolean(hook, "staticTarget", false, modId);
		boolean staticCallee = optionalBoolean(hook, "staticCallee", false, modId);

		// the local variable table is routinely stripped from shipped jars, so a name based selector
		// would work in a development environment and fail in production
		if (hook.containsKey("name")) {
			throw new WasmMetadataException(String.format(
					"mod '%s' %s selects a local variable by name; only 'index' is supported because the local "
					+ "variable table is not present in shipped game jars", modId, where));
		}

		validate(kind, index, valueType, cancellable, at, where, modId);
		return new HookSpec(hookId, id, kind, environment, targetClass, targetMethod, targetDescriptor, at,
				index, cancellable, valueType, require, priority, staticTarget, staticCallee);
	}

	private static void validate(HookKind kind, int index, String valueType, boolean cancellable, AtSpec at,
			String where, String modId) throws WasmMetadataException {
		switch (kind) {
		case MODIFY_VARIABLE:
			if (index == HookSpec.NO_INDEX) {
				throw new WasmMetadataException(String.format(
						"mod '%s' %s is a modifyVariable hook and must declare 'index', the local variable slot", modId, where));
			}

			requireValueType(valueType, where, modId);
			break;
		case MODIFY_ARG:
			if (index == HookSpec.NO_INDEX) {
				throw new WasmMetadataException(String.format(
						"mod '%s' %s is a modifyArg hook and must declare 'index', the argument position", modId, where));
			}

			requireValueType(valueType, where, modId);
			checkInvokeTarget(at, where, modId);
			break;
		case REDIRECT:
			checkInvokeTarget(at, where, modId);
			break;
		case INJECT:
			break;
		}

		if (cancellable && kind != HookKind.INJECT) {
			throw new WasmMetadataException(String.format(
					"mod '%s' %s sets 'cancellable', which only applies to inject hooks", modId, where));
		}
	}

	private static void requireValueType(String valueType, String where, String modId) throws WasmMetadataException {
		if (valueType == null) {
			throw new WasmMetadataException(String.format(
					"mod '%s' %s must declare 'valueType', the JVM descriptor of the value it replaces", modId, where));
		}

		checkFieldDescriptor(valueType, where + ".valueType", modId);
	}

	private static void checkInvokeTarget(AtSpec at, String where, String modId) throws WasmMetadataException {
		if (!"INVOKE".equals(at.getValue()) || at.getTarget() == null) {
			throw new WasmMetadataException(String.format(
					"mod '%s' %s needs at.value=\"INVOKE\" with an at.target naming the call to act on, but has %s",
					modId, where, at));
		}
	}

	private static AtSpec parseAt(CustomValue.CvObject at, String where, String modId) throws WasmMetadataException {
		return new AtSpec(requireString(at, "value", where, modId),
				optionalString(at, "target", null, where, modId),
				optionalInt(at, "ordinal", AtSpec.NO_ORDINAL, where, modId));
	}

	private static ModEnvironment parseEnvironment(String env, String where, String modId) throws WasmMetadataException {
		switch (env) {
		case "*":
			return ModEnvironment.UNIVERSAL;
		case "client":
			return ModEnvironment.CLIENT;
		case "server":
			return ModEnvironment.SERVER;
		default:
			throw new WasmMetadataException(String.format(
					"mod '%s' %s declares env '%s'; expected \"*\", \"client\" or \"server\"", modId, where, env));
		}
	}

	private static void checkInternalName(String name, String where, String modId) throws WasmMetadataException {
		if (name.isEmpty() || name.indexOf('.') >= 0) {
			throw new WasmMetadataException(String.format(
					"mod '%s' %s must be an internal class name using '/' separators, but is '%s'", modId, where, name));
		}
	}

	private static void checkMethodDescriptor(String descriptor, String where, String modId) throws WasmMetadataException {
		if (!descriptor.startsWith("(") || descriptor.indexOf(')') < 0) {
			throw new WasmMetadataException(String.format(
					"mod '%s' %s must be a method descriptor such as \"(I)V\", but is '%s'", modId, where, descriptor));
		}
	}

	private static void checkFieldDescriptor(String descriptor, String where, String modId) throws WasmMetadataException {
		if (descriptor.isEmpty() || "VZBCSIJFD[L".indexOf(descriptor.charAt(0)) < 0) {
			throw new WasmMetadataException(String.format(
					"mod '%s' %s must be a JVM type descriptor such as \"I\" or \"Ljava/lang/String;\", but is '%s'",
					modId, where, descriptor));
		}
	}

	private static CustomValue require(CustomValue.CvObject object, String key, String where, String modId)
			throws WasmMetadataException {
		CustomValue value = object.get(key);

		if (value == null) {
			throw new WasmMetadataException(String.format("mod '%s' %s is missing the required key '%s'", modId, where, key));
		}

		return value;
	}

	private static String requireString(CustomValue.CvObject object, String key, String where, String modId)
			throws WasmMetadataException {
		CustomValue value = require(object, key, where, modId);
		expect(value, CustomValue.CvType.STRING, where + "." + key, modId);
		return value.getAsString();
	}

	private static int requireInt(CustomValue.CvObject object, String key, String where, String modId)
			throws WasmMetadataException {
		CustomValue value = require(object, key, where, modId);
		expect(value, CustomValue.CvType.NUMBER, where + "." + key, modId);
		return value.getAsNumber().intValue();
	}

	private static String optionalString(CustomValue.CvObject object, String key, String fallback, String where,
			String modId) throws WasmMetadataException {
		CustomValue value = object.get(key);
		if (value == null) return fallback;

		expect(value, CustomValue.CvType.STRING, where + "." + key, modId);
		return value.getAsString();
	}

	private static int optionalInt(CustomValue.CvObject object, String key, int fallback, String where, String modId)
			throws WasmMetadataException {
		CustomValue value = object.get(key);
		if (value == null) return fallback;

		expect(value, CustomValue.CvType.NUMBER, where + "." + key, modId);
		return value.getAsNumber().intValue();
	}

	private static boolean optionalBoolean(CustomValue.CvObject object, String key, boolean fallback, String modId)
			throws WasmMetadataException {
		CustomValue value = object.get(key);
		if (value == null) return fallback;

		expect(value, CustomValue.CvType.BOOLEAN, key, modId);
		return value.getAsBoolean();
	}

	private static CustomValue.CvObject asObject(CustomValue value, String where, String modId)
			throws WasmMetadataException {
		expect(value, CustomValue.CvType.OBJECT, where, modId);
		return value.getAsObject();
	}

	private static CustomValue.CvArray asArray(CustomValue value, String where, String modId)
			throws WasmMetadataException {
		expect(value, CustomValue.CvType.ARRAY, where, modId);
		return value.getAsArray();
	}

	private static void expect(CustomValue value, CustomValue.CvType type, String where, String modId)
			throws WasmMetadataException {
		if (value.getType() != type) {
			throw new WasmMetadataException(String.format("mod '%s' %s must be %s but is %s",
					modId, where, type.name().toLowerCase(java.util.Locale.ROOT), value.getType().name().toLowerCase(java.util.Locale.ROOT)));
		}
	}
}
