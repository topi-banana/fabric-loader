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

import java.util.List;

import net.fabricmc.loader.impl.wasm.WasmConstants;

/**
 * Writes the Mixin configuration listing the generated classes.
 *
 * <p>Two things are deliberately absent. There is no {@code refmap}: every name in the generated
 * annotations is already in the runtime namespace, and naming a reference map that does not exist
 * produces a warning whereas omitting the key selects the no-op mapper. There is no
 * {@code compatibilityLevel} either, because the generated classes are Java 8 and Mixin's minimum is
 * never above that, so declaring one could only conflict with a level another mod has raised.
 *
 * <p>{@code injectors.defaultRequire} is 1 on purpose. The target's bytecode cannot be inspected
 * while generating, so a declaration that matches nothing has to be an error rather than a hook that
 * silently never fires.
 */
public final class MixinConfigGenerator {
	private MixinConfigGenerator() { }

	/**
	 * @param modId the owning mod
	 * @param universal classes whose hooks apply to any environment
	 * @param client classes whose hooks are client only
	 * @param server classes whose hooks are server only
	 * @return the configuration's JSON text
	 */
	public static String write(String modId, List<String> universal, List<String> client, List<String> server) {
		StringBuilder sb = new StringBuilder();
		sb.append("{\n");
		sb.append("\t\"required\": true,\n");
		sb.append("\t\"minVersion\": \"0.8\",\n");
		sb.append("\t\"package\": \"").append(WasmConstants.generatedPackage(modId)).append("\",\n");
		appendArray(sb, "mixins", universal);
		appendArray(sb, "client", client);
		appendArray(sb, "server", server);
		sb.append("\t\"injectors\": {\n");
		sb.append("\t\t\"defaultRequire\": 1\n");
		sb.append("\t}\n");
		sb.append("}\n");
		return sb.toString();
	}

	private static void appendArray(StringBuilder sb, String key, List<String> values) {
		sb.append("\t\"").append(key).append("\": [");

		for (int i = 0; i < values.size(); i++) {
			sb.append(i == 0 ? "\n" : ",\n").append("\t\t\"").append(values.get(i)).append('"');
		}

		if (!values.isEmpty()) sb.append('\n').append('\t');

		sb.append("],\n");
	}
}
