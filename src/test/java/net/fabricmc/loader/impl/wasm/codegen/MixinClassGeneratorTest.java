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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.CheckClassAdapter;

import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.impl.metadata.TestCustomValues;
import net.fabricmc.loader.impl.wasm.meta.HookKind;
import net.fabricmc.loader.impl.wasm.meta.WasmMetadataException;
import net.fabricmc.loader.impl.wasm.meta.WasmModSpec;

/**
 * Verifies the generated Mixin classes are well formed bytecode with the annotations Mixin expects,
 * for every injection kind.
 *
 * <p>{@link CheckClassAdapter#verify} is the important part: the generator cannot use
 * {@code COMPUTE_FRAMES} because that would load the target classes, so frames are hand written and
 * a mistake there produces a class that only fails when the game loads the target.
 */
class MixinClassGeneratorTest {
	private static final String MOD_ID = "wasmtestmod";
	private static final String TARGET = "net/minecraft/class_310";

	/**
	 * A resolver standing in for a production environment, where the runtime namespace already is
	 * the one declarations are written in.
	 */
	private static final MappingResolver IDENTITY_RESOLVER = new MappingResolver() {
		@Override
		public Collection<String> getNamespaces() {
			return Collections.singletonList(NamespaceMapper.SOURCE_NAMESPACE);
		}

		@Override
		public String getCurrentRuntimeNamespace() {
			return NamespaceMapper.SOURCE_NAMESPACE;
		}

		@Override
		public String mapClassName(String namespace, String className) {
			return className;
		}

		@Override
		public String unmapClassName(String targetNamespace, String className) {
			return className;
		}

		@Override
		public String mapFieldName(String namespace, String owner, String name, String descriptor) {
			return name;
		}

		@Override
		public String mapMethodName(String namespace, String owner, String name, String descriptor) {
			return name;
		}
	};

	@Test
	@DisplayName("An inject hook on a void target produces a verifiable CallbackInfo handler")
	void generatesVoidInject() throws Exception {
		MethodNode handler = generateOne(hook("inject", "\"target\": {\"class\": \"" + TARGET + "\", "
				+ "\"method\": \"method_1574\", \"descriptor\": \"()V\"}, \"at\": {\"value\": \"HEAD\"}, "
				+ "\"cancellable\": true"));

		assertEquals("(L" + HandlerSignature.CALLBACK_INFO + ";)V", handler.desc);
		assertEquals(Opcodes.ACC_PRIVATE, handler.access);
		assertAnnotation(handler, "Lorg/spongepowered/asm/mixin/injection/Inject;", "cancellable", Boolean.TRUE);
		assertAnnotation(handler, "Lorg/spongepowered/asm/mixin/injection/Inject;", "remap", Boolean.FALSE);
	}

	@Test
	@DisplayName("An inject hook on a value target takes the target's parameters and a returnable callback")
	void generatesReturningInject() throws Exception {
		MethodNode handler = generateOne(hook("inject", "\"target\": {\"class\": \"" + TARGET + "\", "
				+ "\"method\": \"method_1234\", \"descriptor\": \"(ILjava/lang/String;)Z\"}, "
				+ "\"at\": {\"value\": \"RETURN\"}"));

		assertEquals("(ILjava/lang/String;L" + HandlerSignature.CALLBACK_INFO_RETURNABLE + ";)V", handler.desc);
	}

	@Test
	@DisplayName("A static target produces a static handler, as Mixin requires")
	void generatesStaticHandler() throws Exception {
		MethodNode handler = generateOne(hook("inject", "\"target\": {\"class\": \"" + TARGET + "\", "
				+ "\"method\": \"method_static\", \"descriptor\": \"(J)V\"}, \"at\": {\"value\": \"HEAD\"}, "
				+ "\"staticTarget\": true"));

		assertEquals(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, handler.access);
		assertEquals("(JL" + HandlerSignature.CALLBACK_INFO + ";)V", handler.desc);
	}

	@Test
	@DisplayName("A modifyVariable hook takes and returns the declared value type")
	void generatesModifyVariable() throws Exception {
		MethodNode handler = generateOne(hook("modifyVariable", "\"target\": {\"class\": \"" + TARGET + "\", "
				+ "\"method\": \"method_6082\", \"descriptor\": \"(F)V\"}, \"at\": {\"value\": \"HEAD\"}, "
				+ "\"index\": 2, \"valueType\": \"F\""));

		assertEquals("(F)F", handler.desc);
		assertAnnotation(handler, "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;", "index", Integer.valueOf(2));
	}

	@Test
	@DisplayName("A modifyArg hook takes and returns the declared argument type")
	void generatesModifyArg() throws Exception {
		MethodNode handler = generateOne(hook("modifyArg", "\"target\": {\"class\": \"" + TARGET + "\", "
				+ "\"method\": \"method_22710\", \"descriptor\": \"()V\"}, "
				+ "\"at\": {\"value\": \"INVOKE\", \"target\": \"Lnet/minecraft/class_4587;method_22905(FFF)V\"}, "
				+ "\"index\": 1, \"valueType\": \"F\""));

		assertEquals("(F)F", handler.desc);
	}

	@Test
	@DisplayName("A redirect hook on an instance call takes the receiver as its first parameter")
	void generatesInstanceRedirect() throws Exception {
		// uses types that exist on the test class path so that the data flow verifier, which resolves
		// every referenced type, can check the fallback branch that performs the original call
		MethodNode handler = generateOne(hook("redirect", "\"target\": {\"class\": \"" + TARGET + "\", "
				+ "\"method\": \"method_9556\", \"descriptor\": \"()V\"}, "
				+ "\"at\": {\"value\": \"INVOKE\", \"target\": "
				+ "\"Ljava/lang/StringBuilder;append(Ljava/lang/String;)Ljava/lang/StringBuilder;\"}"));

		assertEquals("(Ljava/lang/StringBuilder;Ljava/lang/String;)Ljava/lang/StringBuilder;", handler.desc);
	}

	@Test
	@DisplayName("A redirect hook onto a game type still gets the right descriptor")
	void generatesInstanceRedirectOntoGameType() throws Exception {
		// structural check only: the verifier would try to load net.minecraft.class_2338, which is
		// exactly the situation the generator is built for and cannot be reproduced in a unit test
		ClassNode node = generate(hook("redirect", "\"target\": {\"class\": \"" + TARGET + "\", "
				+ "\"method\": \"method_9556\", \"descriptor\": \"()V\"}, "
				+ "\"at\": {\"value\": \"INVOKE\", \"target\": "
				+ "\"Lnet/minecraft/class_2338;method_10093(Lnet/minecraft/class_2350;)Lnet/minecraft/class_2338;\"}"),
				false);

		assertEquals("(Lnet/minecraft/class_2338;Lnet/minecraft/class_2350;)Lnet/minecraft/class_2338;",
				node.methods.get(0).desc);
	}

	@Test
	@DisplayName("A redirect hook on a static call omits the receiver")
	void generatesStaticRedirect() throws Exception {
		MethodNode handler = generateOne(hook("redirect", "\"target\": {\"class\": \"" + TARGET + "\", "
				+ "\"method\": \"method_9556\", \"descriptor\": \"()V\"}, "
				+ "\"at\": {\"value\": \"INVOKE\", \"target\": \"Lsome/Owner;helper(II)I\"}, "
				+ "\"staticCallee\": true"));

		assertEquals("(II)I", handler.desc);
	}

	@Test
	@DisplayName("Hooks on the same target share one Mixin class")
	void groupsHooksByTarget() throws Exception {
		String hooks = hook("inject", "\"target\": {\"class\": \"" + TARGET + "\", \"method\": \"a\", "
				+ "\"descriptor\": \"()V\"}, \"at\": {\"value\": \"HEAD\"}")
				+ ", " + hook("inject", "\"target\": {\"class\": \"" + TARGET + "\", \"method\": \"b\", "
				+ "\"descriptor\": \"()V\"}, \"at\": {\"value\": \"HEAD\"}");

		ClassNode node = generate(hooks);
		assertEquals(2, node.methods.size(), node.methods.toString());
	}

	@Test
	@DisplayName("The Mixin annotation names the target as a string, since it cannot be loaded")
	void namesTargetAsString() throws Exception {
		ClassNode node = generate(hook("inject", "\"target\": {\"class\": \"" + TARGET + "\", "
				+ "\"method\": \"method_1574\", \"descriptor\": \"()V\"}, \"at\": {\"value\": \"HEAD\"}"));

		// @Mixin is CLASS retention, so it lands in the invisible table; the injectors are RUNTIME
		AnnotationNode mixin = find(node.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;");
		assertNotNull(mixin, "no @Mixin annotation");

		Object targets = value(mixin, "targets");
		assertTrue(targets instanceof List, String.valueOf(targets));
		assertEquals(Collections.singletonList("net.minecraft.class_310"), targets);
		assertEquals(Boolean.FALSE, value(mixin, "remap"));
	}

	@Test
	@DisplayName("Generated classes are abstract and declare no constructor")
	void hasNoConstructor() throws Exception {
		ClassNode node = generate(hook("inject", "\"target\": {\"class\": \"" + TARGET + "\", "
				+ "\"method\": \"method_1574\", \"descriptor\": \"()V\"}, \"at\": {\"value\": \"HEAD\"}"));

		assertTrue((node.access & Opcodes.ACC_ABSTRACT) != 0, "expected an abstract class");
		assertEquals(Opcodes.V1_8, node.version);

		for (MethodNode method : node.methods) {
			assertTrue(!"<init>".equals(method.name), "a Mixin class must not declare a constructor");
		}
	}

	/**
	 * Generates and verifies, returning the single handler method.
	 */
	private static MethodNode generateOne(String hookJson) throws WasmMetadataException {
		ClassNode node = generate(hookJson);
		assertEquals(1, node.methods.size(), node.methods.toString());
		return node.methods.get(0);
	}

	private static ClassNode generate(String hooksJson) throws WasmMetadataException {
		return generate(hooksJson, true);
	}

	/**
	 * Generates from a hook declaration list and checks the result.
	 *
	 * @param dataFlowVerify whether to run the data flow verifier as well as the structural one; it
	 *     resolves every referenced type, so it can only be used when they are all on the class path
	 */
	private static ClassNode generate(String hooksJson, boolean dataFlowVerify) throws WasmMetadataException {
		WasmModSpec spec = WasmModSpec.parse(
				TestCustomValues.parse("{\"abi\": 1, \"hooks\": [" + hooksJson + "]}"), MOD_ID);

		NamespaceMapper mapper = new NamespaceMapper(IDENTITY_RESOLVER);
		List<ResolvedHook> resolved = new ArrayList<>();
		int id = 0;

		for (net.fabricmc.loader.impl.wasm.meta.HookSpec hook : spec.getHooks()) {
			resolved.add(ResolvedHook.resolve(hook, id++, mapper, MOD_ID));
		}

		byte[] bytes = new MixinClassGenerator(MOD_ID)
				.generate(resolved.get(0).getTargetClass(), resolved).getBytes();

		checkStructure(bytes);
		if (dataFlowVerify) verify(bytes);

		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node, 0);
		return node;
	}

	/**
	 * Checks the class is structurally well formed, including the hand written stack map frames.
	 * Resolves no types, so it works for classes referring to the game.
	 */
	private static void checkStructure(byte[] bytes) {
		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(new CheckClassAdapter(node, true), 0);
	}

	/**
	 * Runs ASM's data flow verifier. Note that it reports problems by writing to its printer rather
	 * than throwing, so the output has to be inspected.
	 */
	private static void verify(byte[] bytes) {
		StringWriter out = new StringWriter();
		CheckClassAdapter.verify(new ClassReader(bytes), false, new PrintWriter(out));

		String report = out.toString();
		assertTrue(report.isEmpty(), "bytecode verification failed:\n" + report);
	}

	private static String hook(String kind, String rest) {
		return "{\"kind\": \"" + kind + "\", " + rest + "}";
	}

	private static void assertAnnotation(MethodNode method, String descriptor, String key, Object expected) {
		AnnotationNode annotation = find(method.visibleAnnotations, descriptor);
		assertNotNull(annotation, "missing annotation " + descriptor + " on " + method.name);
		assertEquals(expected, value(annotation, key), key + " of " + descriptor);
	}

	private static AnnotationNode find(List<AnnotationNode> annotations, String descriptor) {
		if (annotations == null) return null;

		for (AnnotationNode annotation : annotations) {
			if (descriptor.equals(annotation.desc)) return annotation;
		}

		return null;
	}

	private static Object value(AnnotationNode annotation, String key) {
		if (annotation.values == null) return null;

		for (int i = 0; i < annotation.values.size(); i += 2) {
			if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
		}

		return null;
	}

	@Test
	@DisplayName("Kinds map onto the injector annotations one to one")
	void mapsKindsToAnnotations() {
		assertEquals(HookKind.INJECT, HookKind.byDeclaredName("inject"));
		assertEquals(HookKind.MODIFY_VARIABLE, HookKind.byDeclaredName("modifyVariable"));
		assertEquals(HookKind.REDIRECT, HookKind.byDeclaredName("redirect"));
		assertEquals(HookKind.MODIFY_ARG, HookKind.byDeclaredName("modifyArg"));
		assertEquals(null, HookKind.byDeclaredName("wrapOperation"));
		assertTrue(Arrays.asList(HookKind.values()).size() == 4);
	}
}
