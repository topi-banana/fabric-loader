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

package net.fabricmc.loader.impl.metadata;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;

import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.impl.lib.gson.JsonReader;

/**
 * Builds {@link CustomValue} instances from JSON text for tests.
 *
 * <p>Lives in this package because {@code CustomValueImpl} is package private; tests elsewhere would
 * otherwise have to construct a whole {@code fabric.mod.json} to get at a custom value.
 */
public final class TestCustomValues {
	private TestCustomValues() { }

	public static CustomValue parse(String json) {
		try (JsonReader reader = new JsonReader(new StringReader(json))) {
			return CustomValueImpl.readCustomValue(reader);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (ParseMetadataException e) {
			throw new IllegalArgumentException("not valid JSON for a custom value: " + json, e);
		}
	}
}
