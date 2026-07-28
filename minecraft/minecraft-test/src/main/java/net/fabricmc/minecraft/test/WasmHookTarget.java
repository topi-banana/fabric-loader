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

package net.fabricmc.minecraft.test;

/**
 * Target of the WebAssembly mod hook exercised by {@code WasmModTest}.
 *
 * <p>Deliberately not a Minecraft class: obfuscated names would drag mapping resolution into a test
 * that is about whether a generated Mixin reaches the guest at all, and this way the same fixture
 * works whatever namespace the game is running in.
 */
public class WasmHookTarget {
	/**
	 * @return 1 unless a WebAssembly hook substitutes the return value
	 */
	public int compute() {
		return 1;
	}

	/**
	 * @return the sum, unless a WebAssembly hook cancels and substitutes
	 */
	public int add(int a, int b) {
		return a + b;
	}
}
