package info.cho.universalMixin.transformer;

import info.cho.universalMixin.api.At;
import info.cho.universalMixin.api.Inject;
import info.cho.universalMixin.metadata.MixinMetadata;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Comparator;

/** Inserts calls to validated mixin callbacks into concrete target methods. */
final class InjectionProcessor {
    private InjectionProcessor() {}

    static void apply(ClassNode target, Class<?> mixinClass) {
        if ((mixinClass.getModifiers() & Opcodes.ACC_PUBLIC) == 0) {
            throw new IllegalArgumentException("Mixin class must be public: " + mixinClass.getName());
        }
        String owner = Type.getInternalName(mixinClass);
        var callbacks = new ArrayList<>(MixinMetadata.read(mixinClass).methods);
        callbacks.sort(Comparator.comparing((MethodNode method) -> method.name).thenComparing(method -> method.desc));

        for (MethodNode callback : callbacks) {
            var inject = MixinMetadata.annotation(callback, Inject.class);
            if (inject == null) {
                continue;
            }
            validateCallback(callback);
            if (ShadowProcessor.usesShadow(callback, mixinClass)) {
                throw new IllegalArgumentException("@Shadow fields require a copied @Replace body, not @Inject: " + callback.name);
            }
            String methodName = (String) inject.get("method");
            At at = inject.containsKey("at") ? At.valueOf(((String[]) inject.get("at"))[1]) : At.HEAD;

            boolean found = false;
            for (MethodNode method : target.methods) {
                // Constructors, static initializers, abstract and native methods are unsupported.
                if (!method.name.equals(methodName) || method.name.startsWith("<")
                        || method.instructions.size() == 0) {
                    continue;
                }
                found = true;
                switch (at) {
                    case HEAD -> method.instructions.insert(
                            createCallback(owner, callback.name, mixinClass.isInterface()));
                    case RETURN -> injectReturn(method, owner, callback.name, mixinClass.isInterface());
                }
            }
            if (!found) {
                throw new IllegalArgumentException("Target method not found: "
                        + target.name.replace('/', '.') + "." + methodName);
            }
        }
    }

    private static void validateCallback(MethodNode callback) {
        int requiredAccess = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
        if ((callback.access & requiredAccess) != requiredAccess) {
            throw new IllegalArgumentException("@Inject callback must be public static: " + callback.name);
        }
        if (!callback.desc.equals("()V")) {
            throw new IllegalArgumentException("@Inject callback must have signature ()V: " + callback.name + callback.desc);
        }
    }

    private static void injectReturn(MethodNode method, String owner, String callbackName, boolean isInterface) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            int opcode = instruction.getOpcode();
            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                method.instructions.insertBefore(instruction, createCallback(owner, callbackName, isInterface));
            }
        }
    }

    private static InsnList createCallback(String owner, String callbackName, boolean isInterface) {
        InsnList instructions = new InsnList();
        instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, callbackName, "()V", isInterface));
        return instructions;
    }
}
