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

import org.objectweb.asm.Type;

import net.fabricmc.loader.impl.wasm.meta.HookSpec;
import net.fabricmc.loader.impl.wasm.meta.WasmMetadataException;

/**
 * The signature of a generated Mixin handler, derived from a hook declaration.
 *
 * <p>Mixin checks the handler's descriptor against the target when the target class is loaded, and
 * the rules differ per injector, so this is where the four kinds diverge.
 */
public final class HandlerSignature {
	/**
	 * Internal name of Mixin's callback object for void targets.
	 */
	public static final String CALLBACK_INFO = "org/spongepowered/asm/mixin/injection/callback/CallbackInfo";

	/**
	 * Internal name of Mixin's callback object for value returning targets.
	 */
	public static final String CALLBACK_INFO_RETURNABLE =
			"org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable";

	private final String descriptor;
	private final Type[] forwardedTypes;
	private final Type returnType;
	private final boolean staticHandler;
	private final boolean receivesCallbackInfo;

	private HandlerSignature(String descriptor, Type[] forwardedTypes, Type returnType, boolean staticHandler,
			boolean receivesCallbackInfo) {
		this.descriptor = descriptor;
		this.forwardedTypes = forwardedTypes;
		this.returnType = returnType;
		this.staticHandler = staticHandler;
		this.receivesCallbackInfo = receivesCallbackInfo;
	}

	/**
	 * @return the handler's JVM descriptor
	 */
	public String getDescriptor() {
		return descriptor;
	}

	/**
	 * @return the handler parameters that are forwarded to the guest, in order; excludes the
	 *     trailing callback object of an inject handler
	 */
	public Type[] getForwardedTypes() {
		return forwardedTypes;
	}

	public Type getReturnType() {
		return returnType;
	}

	public boolean isStaticHandler() {
		return staticHandler;
	}

	/**
	 * @return whether the last parameter is a Mixin callback object rather than a forwarded value
	 */
	public boolean receivesCallbackInfo() {
		return receivesCallbackInfo;
	}

	/**
	 * @return the callback object's internal name, or null if the handler receives none
	 */
	public String getCallbackInfoType(HookSpec spec) {
		if (!receivesCallbackInfo) return null;

		return Type.getReturnType(spec.getTargetDescriptor()).getSort() == Type.VOID
				? CALLBACK_INFO : CALLBACK_INFO_RETURNABLE;
	}

	/**
	 * Derives the handler signature for a hook whose names are already in the runtime namespace.
	 */
	public static HandlerSignature of(HookSpec spec, String modId) throws WasmMetadataException {
		switch (spec.getKind()) {
		case INJECT:
			return forInject(spec);
		case MODIFY_VARIABLE:
		case MODIFY_ARG:
			return forValueReplacement(spec);
		case REDIRECT:
			return forRedirect(spec, modId);
		default:
			throw new WasmMetadataException("unhandled hook kind " + spec.getKind());
		}
	}

	/**
	 * {@code (targetParams..., CallbackInfo[Returnable])V}.
	 */
	private static HandlerSignature forInject(HookSpec spec) {
		Type[] params = Type.getArgumentTypes(spec.getTargetDescriptor());
		boolean returnsValue = Type.getReturnType(spec.getTargetDescriptor()).getSort() != Type.VOID;
		String callback = returnsValue ? CALLBACK_INFO_RETURNABLE : CALLBACK_INFO;

		StringBuilder sb = new StringBuilder("(");

		for (Type param : params) {
			sb.append(param.getDescriptor());
		}

		sb.append('L').append(callback).append(";)V");

		return new HandlerSignature(sb.toString(), params, Type.VOID_TYPE, spec.isStaticTarget(), true);
	}

	/**
	 * {@code (T)T} where {@code T} is the declared value type.
	 */
	private static HandlerSignature forValueReplacement(HookSpec spec) {
		Type value = Type.getType(spec.getValueType());

		return new HandlerSignature("(" + value.getDescriptor() + ")" + value.getDescriptor(),
				new Type[] {value}, value, spec.isStaticTarget(), false);
	}

	/**
	 * {@code (receiver?, calleeParams...)calleeReturn}. The receiver is a normal first parameter,
	 * not {@code this}, and is absent when the intercepted call is static.
	 */
	private static HandlerSignature forRedirect(HookSpec spec, String modId) throws WasmMetadataException {
		MemberRef callee = MemberRef.parse(spec.getAt().getTarget(), "hook " + spec.getId() + " at.target", modId);
		Type[] calleeParams = Type.getArgumentTypes(callee.getDescriptor());
		Type calleeReturn = Type.getReturnType(callee.getDescriptor());

		Type[] forwarded;

		if (spec.isStaticCallee()) {
			forwarded = calleeParams;
		} else {
			forwarded = new Type[calleeParams.length + 1];
			forwarded[0] = Type.getObjectType(callee.getOwner());
			System.arraycopy(calleeParams, 0, forwarded, 1, calleeParams.length);
		}

		StringBuilder sb = new StringBuilder("(");

		for (Type param : forwarded) {
			sb.append(param.getDescriptor());
		}

		sb.append(')').append(calleeReturn.getDescriptor());

		return new HandlerSignature(sb.toString(), forwarded, calleeReturn, spec.isStaticTarget(), false);
	}
}
