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

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import net.fabricmc.loader.impl.wasm.WasmConstants;
import net.fabricmc.loader.impl.wasm.meta.HookSpec;

/**
 * Emits a real Mixin class for the hooks declared by a WebAssembly mod.
 *
 * <p>Generating an ordinary annotated class, rather than reaching into the transformer chain, means
 * the whole of Mixin applies unchanged -- selectors, injection points, conflict detection with other
 * mods, and its error reporting.
 *
 * <p>Two constraints shape the output. The target class cannot be loaded while this runs, so targets
 * are named as strings and {@link ClassWriter#COMPUTE_FRAMES} must not be used: computing frames
 * makes ASM call {@code getCommonSuperClass}, which loads both types and would fail on exactly the
 * classes being targeted. Handler bodies are therefore kept free of merge points, so the only frame
 * needed is a single unchanged one in the redirect fallback.
 */
public final class MixinClassGenerator {
	/**
	 * {@code @Mixin} is declared {@code RetentionPolicy.CLASS}, so it belongs in the invisible
	 * annotation table. Emitting it as a visible one leaves Mixin reporting a missing
	 * {@code @Mixin} on a class that plainly has one.
	 */
	private static final boolean MIXIN_VISIBLE = false;

	/**
	 * The injector annotations, unlike {@code @Mixin}, are declared {@code RetentionPolicy.RUNTIME}
	 * and belong in the visible table. Putting them in the wrong one is silent: Mixin still merges
	 * the handler into the target, it simply never treats it as an injector, so the target runs
	 * unmodified and no error is reported.
	 */
	private static final boolean INJECTOR_VISIBLE = true;

	private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
	private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
	private static final String MODIFY_VARIABLE = "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;";
	private static final String MODIFY_ARG = "Lorg/spongepowered/asm/mixin/injection/ModifyArg;";
	private static final String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
	private static final String AT = "Lorg/spongepowered/asm/mixin/injection/At;";

	private static final String HOOKS = "net/fabricmc/loader/impl/wasm/WasmHooks";

	private final String modId;

	public MixinClassGenerator(String modId) {
		this.modId = modId;
	}

	/**
	 * A generated class, ready to be written out and listed in a Mixin configuration.
	 */
	public static final class Generated {
		private final String simpleName;
		private final String internalName;
		private final byte[] bytes;

		Generated(String simpleName, String internalName, byte[] bytes) {
			this.simpleName = simpleName;
			this.internalName = internalName;
			this.bytes = bytes;
		}

		/**
		 * @return the name to list in the configuration's {@code mixins} array
		 */
		public String getSimpleName() {
			return simpleName;
		}

		public String getInternalName() {
			return internalName;
		}

		public byte[] getBytes() {
			return bytes;
		}
	}

	/**
	 * Generates one class holding every hook that targets the same class.
	 *
	 * @param targetClass internal name of the target, in the runtime namespace
	 * @param hooks the hooks aimed at it, all sharing an environment
	 */
	public Generated generate(String targetClass, List<ResolvedHook> hooks) {
		String simpleName = classNameFor(targetClass);
		String internalName = WasmConstants.generatedPackage(modId).replace('.', '/') + "/" + simpleName;

		// COMPUTE_MAXS only: frames are emitted by hand, see the class comment
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, internalName, null,
				"java/lang/Object", null);

		// a Mixin class must not declare a constructor, so none is emitted
		AnnotationVisitor mixin = writer.visitAnnotation(MIXIN, MIXIN_VISIBLE);
		AnnotationVisitor targets = mixin.visitArray("targets");
		targets.visit(null, targetClass.replace('/', '.'));
		targets.visitEnd();
		mixin.visit("remap", Boolean.FALSE);
		mixin.visitEnd();

		for (ResolvedHook hook : hooks) {
			emitHandler(writer, hook);
		}

		writer.visitEnd();
		return new Generated(simpleName, internalName, writer.toByteArray());
	}

	private void emitHandler(ClassWriter writer, ResolvedHook hook) {
		HandlerSignature signature = hook.getSignature();
		int access = Opcodes.ACC_PRIVATE | (signature.isStaticHandler() ? Opcodes.ACC_STATIC : 0);

		MethodVisitor mv = writer.visitMethod(access, hook.getHandlerName(), signature.getDescriptor(), null, null);
		annotate(mv, hook);
		mv.visitCode();

		switch (hook.getSpec().getKind()) {
		case INJECT:
			emitInjectBody(mv, hook);
			break;
		case MODIFY_VARIABLE:
		case MODIFY_ARG:
			emitValueBody(mv, hook);
			break;
		case REDIRECT:
			emitRedirectBody(mv, hook);
			break;
		default:
			throw new IllegalStateException("unhandled hook kind " + hook.getSpec().getKind());
		}

		mv.visitMaxs(0, 0);
		mv.visitEnd();
	}

	/**
	 * {@code begin; push...; inject(id, ci)} -- the bridge decides whether to cancel.
	 */
	private void emitInjectBody(MethodVisitor mv, ResolvedHook hook) {
		int callbackSlot = pushArguments(mv, hook);
		boolean returnsValue = Type.getReturnType(hook.getSpec().getTargetDescriptor()).getSort() != Type.VOID;

		mv.visitLdcInsn(hook.getGlobalHookId());
		mv.visitVarInsn(Opcodes.ALOAD, callbackSlot);

		if (returnsValue) {
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "injectReturnable",
					"(IL" + HandlerSignature.CALLBACK_INFO_RETURNABLE + ";)V", false);
		} else {
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "inject",
					"(IL" + HandlerSignature.CALLBACK_INFO + ";)V", false);
		}

		mv.visitInsn(Opcodes.RETURN);
	}

	/**
	 * {@code begin; push...; return valueX(id, current)} -- the current value doubles as the
	 * fallback when the guest declines to replace it.
	 */
	private void emitValueBody(MethodVisitor mv, ResolvedHook hook) {
		HandlerSignature signature = hook.getSignature();
		pushArguments(mv, hook);

		Type value = signature.getReturnType();
		mv.visitLdcInsn(hook.getGlobalHookId());
		loadSlot(mv, value, signature.isStaticHandler() ? 0 : 1);
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, valueMethod(value),
				"(I" + erase(value).getDescriptor() + ")" + erase(value).getDescriptor(), false);

		if (value.getSort() == Type.OBJECT || value.getSort() == Type.ARRAY) {
			mv.visitTypeInsn(Opcodes.CHECKCAST, value.getInternalName());
		}

		mv.visitInsn(value.getOpcode(Opcodes.IRETURN));
	}

	/**
	 * The one branching handler. Mixin deletes the original call, so declining to act still has to
	 * produce a value: the fallback performs the call itself.
	 */
	private void emitRedirectBody(MethodVisitor mv, ResolvedHook hook) {
		HandlerSignature signature = hook.getSignature();
		pushArguments(mv, hook);

		mv.visitLdcInsn(hook.getGlobalHookId());
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "redirectStatus", "(I)I", false);

		Label fallback = new Label();
		mv.visitJumpInsn(Opcodes.IFEQ, fallback);

		// the guest supplied a value
		Type result = signature.getReturnType();
		mv.visitLdcInsn(hook.getGlobalHookId());
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, resultMethod(result),
				"(I)" + erase(result).getDescriptor(), false);

		if (result.getSort() == Type.OBJECT || result.getSort() == Type.ARRAY) {
			mv.visitTypeInsn(Opcodes.CHECKCAST, result.getInternalName());
		}

		mv.visitInsn(result.getOpcode(Opcodes.IRETURN));

		// both arms return, so the stack is empty here and an unchanged frame is enough
		mv.visitLabel(fallback);
		mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		emitOriginalCall(mv, hook);
	}

	private void emitOriginalCall(MethodVisitor mv, ResolvedHook hook) {
		HandlerSignature signature = hook.getSignature();
		MemberRef callee = hook.getAtTarget();
		Type[] forwarded = signature.getForwardedTypes();
		int slot = signature.isStaticHandler() ? 0 : 1;

		for (Type type : forwarded) {
			loadSlot(mv, type, slot);
			slot += type.getSize();
		}

		mv.visitMethodInsn(hook.getSpec().isStaticCallee() ? Opcodes.INVOKESTATIC : Opcodes.INVOKEVIRTUAL,
				callee.getOwner(), callee.getName(), callee.getDescriptor(), false);
		mv.visitInsn(signature.getReturnType().getOpcode(Opcodes.IRETURN));
	}

	/**
	 * Emits {@code begin} followed by a push per forwarded argument.
	 *
	 * @return the local slot just past the forwarded arguments, which is where an inject handler's
	 *     callback object sits
	 */
	private int pushArguments(MethodVisitor mv, ResolvedHook hook) {
		HandlerSignature signature = hook.getSignature();

		mv.visitLdcInsn(hook.getGlobalHookId());
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "begin", "(I)V", false);

		int slot = 0;

		if (!signature.isStaticHandler()) {
			// in a Mixin, `this` is the target instance; the guest sees it as the first argument
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "pushObject", "(Ljava/lang/Object;)V", false);
			slot = 1;
		}

		for (Type type : signature.getForwardedTypes()) {
			loadSlot(mv, type, slot);
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, pushMethod(type),
					"(" + erase(type).getDescriptor() + ")V", false);
			slot += type.getSize();
		}

		return slot;
	}

	private static void loadSlot(MethodVisitor mv, Type type, int slot) {
		mv.visitVarInsn(type.getOpcode(Opcodes.ILOAD), slot);
	}

	/**
	 * @return the type the bridge declares, which is {@code Object} for every reference type
	 */
	private static Type erase(Type type) {
		return type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY
				? Type.getObjectType("java/lang/Object") : type;
	}

	private static String pushMethod(Type type) {
		switch (type.getSort()) {
		case Type.BOOLEAN: return "pushBoolean";
		case Type.BYTE: return "pushByte";
		case Type.CHAR: return "pushChar";
		case Type.SHORT: return "pushShort";
		case Type.INT: return "pushInt";
		case Type.LONG: return "pushLong";
		case Type.FLOAT: return "pushFloat";
		case Type.DOUBLE: return "pushDouble";
		default: return "pushObject";
		}
	}

	private static String valueMethod(Type type) {
		return "value" + capitalized(type);
	}

	private static String resultMethod(Type type) {
		return "result" + capitalized(type);
	}

	private static String capitalized(Type type) {
		switch (type.getSort()) {
		case Type.BOOLEAN: return "Boolean";
		case Type.BYTE: return "Byte";
		case Type.CHAR: return "Char";
		case Type.SHORT: return "Short";
		case Type.INT: return "Int";
		case Type.LONG: return "Long";
		case Type.FLOAT: return "Float";
		case Type.DOUBLE: return "Double";
		default: return "Object";
		}
	}

	/**
	 * @return a Java identifier derived from the target's name, kept unique by a hash of the whole
	 *     internal name so that nested and same-named classes cannot collide
	 */
	static String classNameFor(String targetClass) {
		int lastSlash = targetClass.lastIndexOf('/');
		String simple = lastSlash < 0 ? targetClass : targetClass.substring(lastSlash + 1);
		StringBuilder sb = new StringBuilder("Mixin_");

		for (int i = 0; i < simple.length(); i++) {
			char c = simple.charAt(i);
			sb.append(Character.isJavaIdentifierPart(c) ? c : '_');
		}

		return sb.append('_').append(String.format("%08x", targetClass.hashCode())).toString();
	}

	/**
	 * Emits the injector annotation for a hook, with {@code remap = false} throughout because every
	 * name has already been resolved to the runtime namespace.
	 */
	private void annotate(MethodVisitor mv, ResolvedHook hook) {
		HookSpec spec = hook.getSpec();
		String descriptor;

		switch (spec.getKind()) {
		case INJECT: descriptor = INJECT; break;
		case MODIFY_VARIABLE: descriptor = MODIFY_VARIABLE; break;
		case MODIFY_ARG: descriptor = MODIFY_ARG; break;
		case REDIRECT: descriptor = REDIRECT; break;
		default: throw new IllegalStateException("unhandled hook kind " + spec.getKind());
		}

		AnnotationVisitor av = mv.visitAnnotation(descriptor, INJECTOR_VISIBLE);

		AnnotationVisitor method = av.visitArray("method");
		method.visit(null, hook.getTargetSelector());
		method.visitEnd();

		// Inject takes At[]; the other three take a single At
		if (spec.getKind() == net.fabricmc.loader.impl.wasm.meta.HookKind.INJECT) {
			AnnotationVisitor array = av.visitArray("at");
			emitAt(array.visitAnnotation(null, AT), hook);
			array.visitEnd();
		} else {
			emitAt(av.visitAnnotation("at", AT), hook);
		}

		if (spec.getKind() == net.fabricmc.loader.impl.wasm.meta.HookKind.INJECT && spec.isCancellable()) {
			av.visit("cancellable", Boolean.TRUE);
		}

		if (spec.getIndex() != HookSpec.NO_INDEX) {
			av.visit("index", Integer.valueOf(spec.getIndex()));
		}

		av.visit("require", Integer.valueOf(spec.getRequire()));
		av.visit("remap", Boolean.FALSE);
		av.visitEnd();
	}

	private void emitAt(AnnotationVisitor at, ResolvedHook hook) {
		at.visit("value", hook.getSpec().getAt().getValue());

		if (hook.getAtTarget() != null) {
			at.visit("target", hook.getAtTarget().toSelector());
		}

		if (hook.getSpec().getAt().hasOrdinal()) {
			at.visit("ordinal", Integer.valueOf(hook.getSpec().getAt().getOrdinal()));
		}

		at.visit("remap", Boolean.FALSE);
		at.visitEnd();
	}
}
