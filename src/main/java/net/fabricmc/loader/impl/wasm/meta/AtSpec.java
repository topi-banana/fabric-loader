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
 * A declared injection point, mapping onto Mixin's {@code @At}.
 */
public final class AtSpec {
	/**
	 * Used for {@code ordinal} when the declaration leaves it out.
	 */
	public static final int NO_ORDINAL = -1;

	private final String value;
	private final String target;
	private final int ordinal;

	public AtSpec(String value, String target, int ordinal) {
		this.value = value;
		this.target = target;
		this.ordinal = ordinal;
	}

	/**
	 * @return the injection point kind, for example {@code HEAD}, {@code RETURN} or {@code INVOKE}
	 */
	public String getValue() {
		return value;
	}

	/**
	 * @return the member the injection point refers to, or null for kinds that need none
	 */
	public String getTarget() {
		return target;
	}

	public int getOrdinal() {
		return ordinal;
	}

	public boolean hasOrdinal() {
		return ordinal != NO_ORDINAL;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("@At(").append(value);
		if (target != null) sb.append(", target=").append(target);
		if (hasOrdinal()) sb.append(", ordinal=").append(ordinal);
		return sb.append(')').toString();
	}
}
