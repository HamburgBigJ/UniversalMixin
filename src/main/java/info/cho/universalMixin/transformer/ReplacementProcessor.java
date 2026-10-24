package info.cho.universalMixin.transformer;

import info.cho.universalMixin.api.Inject;
import info.cho.universalMixin.api.Replace;
import info.cho.universalMixin.metadata.MixinMetadata;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.Set;

/** Replaces target bodies by delegation or by copying code that uses target fields. */
final class ReplacementProcessor {
    private ReplacementProcessor() {}

    static void apply(ClassNode target, Class<?> mixin, Set<String> replaced) {
        ShadowProcessor.validate(target, mixin);
        for (MethodNode callback : MixinMetadata.read(mixin).methods) {
            var annotation = MixinMetadata.annotation(callback, Replace.class);
            if (annotation == null) {
                continue;
            }
            validateCallback(mixin, callback);
            String name = (String) annotation.get("method");
            String descriptor = (String) annotation.getOrDefault("descriptor", "");
            var matches = target.methods.stream()
                    .filter(method -> method.name.equals(name))
                    .filter(method -> descriptor.isEmpty() || method.desc.equals(descriptor))
                    .toList();
            if (matches.isEmpty()) {
                throw new IllegalArgumentException("Replacement target not found: " + target.name + "." + name + descriptor);
            }
            for (MethodNode method : matches) {
                replace(target, method, callback, mixin, replaced);
            }
        }
    }

    private static void replace(ClassNode target, MethodNode method, MethodNode callback,
                                Class<?> mixin, Set<String> replaced) {
        if (method.name.startsWith("<") || (method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
            throw new IllegalArgumentException("Cannot replace constructor, initializer, abstract or native method: " + method.name);
        }
        validateSignature(target, method, callback);
        if (!replaced.add(method.name + method.desc)) {
            throw new IllegalArgumentException("Multiple replacements for " + target.name + "." + method.name + method.desc);
        }
        if (!isStatic(callback) || ShadowProcessor.usesShadow(callback, mixin)) {
            ShadowProcessor.copyBody(target, method, callback, mixin);
        } else {
            delegateToCallback(method, callback, mixin);
        }
    }

    private static void validateCallback(Class<?> mixin, MethodNode callback) {
        boolean publicClass = (mixin.getModifiers() & Opcodes.ACC_PUBLIC) != 0;
        boolean publicMethod = (callback.access & Opcodes.ACC_PUBLIC) != 0;
        boolean concreteMethod = (callback.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0;
        if (!publicClass || !publicMethod || !concreteMethod
                || MixinMetadata.annotation(callback, Inject.class) != null) {
            throw new IllegalArgumentException("@Replace requires a public concrete callback without @Inject: "
                    + mixin.getName() + "." + callback.name + callback.desc);
        }
    }

    private static void validateSignature(ClassNode target, MethodNode method, MethodNode callback) {
        if (!isStatic(callback)) {
            if (isStatic(method) || !callback.desc.equals(method.desc)) {
                throw new IllegalArgumentException("Instance replacement must match an instance target's signature: "
                        + method.name + method.desc);
            }
            return;
        }
        String expected = callbackDescriptor(method, Type.getType(Object.class));
        String typedExpected = callbackDescriptor(method, Type.getObjectType(target.name));
        if (!callback.desc.equals(expected) && !callback.desc.equals(typedExpected)) {
            throw new IllegalArgumentException("@Replace callback must have descriptor " + expected
                    + (isStatic(method) ? "" : " or " + typedExpected) + ": " + callback.name + callback.desc);
        }
    }

    private static String callbackDescriptor(MethodNode method, Type receiver) {
        if (isStatic(method)) {
            return method.desc;
        }
        Type[] arguments = Type.getArgumentTypes(method.desc);
        Type[] callbackArguments = new Type[arguments.length + 1];
        callbackArguments[0] = receiver;
        System.arraycopy(arguments, 0, callbackArguments, 1, arguments.length);
        return Type.getMethodDescriptor(Type.getReturnType(method.desc), callbackArguments);
    }

    private static void delegateToCallback(MethodNode method, MethodNode callback, Class<?> mixin) {
        method.instructions.clear();
        method.tryCatchBlocks.clear();
        if (method.localVariables != null) {
            method.localVariables.clear();
        }
        method.visibleLocalVariableAnnotations = null;
        method.invisibleLocalVariableAnnotations = null;

        int slot = 0;
        if (!isStatic(method)) {
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, slot++));
        }
        for (Type argument : Type.getArgumentTypes(method.desc)) {
            method.instructions.add(new VarInsnNode(argument.getOpcode(Opcodes.ILOAD), slot));
            slot += argument.getSize();
        }
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Type.getInternalName(mixin),
                callback.name, callback.desc, mixin.isInterface()));
        method.instructions.add(new InsnNode(Type.getReturnType(method.desc).getOpcode(Opcodes.IRETURN)));
        method.maxLocals = slot;
        method.maxStack = 0;
    }

    private static boolean isStatic(MethodNode method) {
        return (method.access & Opcodes.ACC_STATIC) != 0;
    }
}
