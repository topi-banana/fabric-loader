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

package net.fabricmc.loader.impl.wasm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.fabricmc.loader.impl.metadata.TestCustomValues;
import net.fabricmc.loader.impl.wasm.meta.WasmMetadataException;
import net.fabricmc.loader.impl.wasm.meta.WasmModSpec;
import net.fabricmc.loader.impl.wasm.runtime.HandleTable;
import net.fabricmc.loader.impl.wasm.runtime.HookArgs;
import net.fabricmc.loader.impl.wasm.runtime.HookOutcome;
import net.fabricmc.loader.impl.wasm.runtime.ValueKind;
import net.fabricmc.loader.impl.wasm.runtime.WasmModInstance;

/**
 * Drives a real guest through the Canonical ABI implementation.
 *
 * <p>The fixture is a hand written module that answers each hook id with a different {@code outcome}
 * case, so this covers both directions: the host lowering a {@code list<value>} into linear memory,
 * and lifting back the variant the guest returns, including its string payload. Getting the layout
 * wrong here produces plausible looking rubbish rather than an error, which is why the guest echoes
 * an argument back.
 */
class CanonicalAbiRoundTripTest {
	private static final Path FIXTURE = Paths.get("src/test/resources/testing/wasm/abiGuest.wasm");

	/**
	 * Matches the hook ids the fixture branches on.
	 */
	private static final int HOOK_PROCEED = 0;
	private static final int HOOK_CANCEL = 1;
	private static final int HOOK_RETURN_ARG_COUNT = 2;
	private static final int HOOK_REPLACE_DOUBLE = 3;
	private static final int HOOK_ECHO_FIRST_ARG = 4;
	private static final int HOOK_FAILED = 5;

	private WasmModInstance instance;

	/**
	 * A fresh instance per test: a guest reported failure deliberately disables the instance for
	 * good, so sharing one would make the tests order dependent.
	 */
	@BeforeEach
	void start() throws WasmMetadataException {
		WasmModSpec spec = WasmModSpec.parse(TestCustomValues.parse("{\"abi\": 1, \"hooks\": []}"), "abiguest");
		instance = new WasmModInstance(new WasmModEntry("abiguest", FIXTURE, FIXTURE, spec));
		instance.start();
	}

	@Test
	@DisplayName("A guest that proceeds produces the shared no-op outcome")
	void liftsProceed() {
		HookOutcome outcome = dispatch(HOOK_PROCEED);

		assertSame(HookOutcome.PROCEED_OUTCOME, outcome);
		assertFalse(outcome.isCancel());
		assertFalse(outcome.hasValue());
	}

	@Test
	@DisplayName("A guest that cancels is understood as a cancellation without a value")
	void liftsCancel() {
		HookOutcome outcome = dispatch(HOOK_CANCEL);

		assertTrue(outcome.isCancel());
		assertFalse(outcome.hasValue());
	}

	@Test
	@DisplayName("The guest sees exactly the arguments that were pushed")
	void lowersArgumentCount() {
		HookArgs args = HookArgs.current();
		args.begin(HOOK_RETURN_ARG_COUNT);
		args.pushObject("receiver");
		args.pushInt(7);
		args.pushLong(8L);

		HookOutcome outcome = instance.dispatch(HOOK_RETURN_ARG_COUNT, args);

		assertTrue(outcome.hasValue());
		assertEquals(ValueKind.I32, outcome.getValueKind());
		assertEquals(3, outcome.asInt());
	}

	@Test
	@DisplayName("An empty argument list needs no allocation and is seen as empty")
	void lowersEmptyArgumentList() {
		assertEquals(0, dispatch(HOOK_RETURN_ARG_COUNT).asInt());
	}

	@Test
	@DisplayName("A replaced double survives the round trip bit for bit")
	void liftsDouble() {
		HookOutcome outcome = dispatch(HOOK_REPLACE_DOUBLE);

		assertTrue(outcome.hasValue());
		assertEquals(ValueKind.F64, outcome.getValueKind());
		assertEquals(2.5d, outcome.asDouble(), 0.0d);
	}

	@Test
	@DisplayName("The list is laid out at the stride the guest expects")
	void lowersListStride() {
		// the guest reads args[0].bits from argsPtr + 8, so a wrong stride or field offset shows up
		// here as a mangled value rather than as an error
		HookArgs args = HookArgs.current();
		args.begin(HOOK_ECHO_FIRST_ARG);
		args.pushInt(0x5eed);
		args.pushInt(0xbad);

		assertEquals(0x5eed, instance.dispatch(HOOK_ECHO_FIRST_ARG, args).asInt());
	}

	@Test
	@DisplayName("A float argument is carried in its raw bits")
	void lowersFloatBits() {
		HookArgs args = HookArgs.current();
		args.begin(HOOK_ECHO_FIRST_ARG);
		args.pushFloat(1.5f);

		assertEquals(Float.floatToRawIntBits(1.5f), instance.dispatch(HOOK_ECHO_FIRST_ARG, args).asInt());
	}

	@Test
	@DisplayName("A guest reported failure disables the mod instead of propagating into game code")
	void handlesGuestFailure() {
		// the failed case carries a string, so this also exercises lifting one out of linear memory
		HookOutcome outcome = dispatch(HOOK_FAILED);

		// the dispatcher swallows the error and falls through, because a hook runs inside woven code
		assertSame(HookOutcome.PROCEED_OUTCOME, outcome);

		// once broken, the instance stops calling into the guest at all
		assertSame(HookOutcome.PROCEED_OUTCOME, dispatch(HOOK_CANCEL));
	}

	private HookOutcome dispatch(int hookId) {
		HookArgs args = HookArgs.current();
		args.begin(hookId);
		return instance.dispatch(hookId, args);
	}

	@Test
	@DisplayName("Handles are scoped to one dispatch and rejected afterwards")
	void scopesHandles() {
		HandleTable handles = new HandleTable();

		int mark = handles.openScope();
		Object value = Collections.singletonList("kept alive while the scope is open");
		int handle = handles.allocate(value);

		assertSame(value, handles.resolve(handle));
		assertEquals(1, handles.liveCount());

		handles.closeScope(mark);

		assertEquals(0, handles.liveCount());
		// the generation counter makes the stale handle detectable instead of resolving to whatever
		// object later takes that slot
		assertSame(null, handles.resolveOrNull(handle));
		assertSame(null, handles.resolveOrNull(HandleTable.NULL_HANDLE));
	}
}
