package info.cho.universalMixin.transformer;

import info.cho.universalMixin.api.Shadow;
import info.cho.universalMixin.metadata.MixinMetadata;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.MethodRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.HashMap;
import java.util.Map;

/** Binds shadow declarations to existing target fields and remaps copied method bodies. */
final class ShadowProcessor {
    private static final ClassValue<Map<String, String>> SHADOWS = new ClassValue<>() {
        @Override
        protected Map<String, String> computeValue(Class<?> mixin) {
            Map<String, String> fields = new HashMap<>();
            for (FieldNode field : MixinMetadata.read(mixin).fields) {
                var annotation = MixinMetadata.annotation(field, Shadow.class);
                if (annotation != null) {
                    String alias = (String) annotation.getOrDefault("value", "");
                    fields.put(field.name, alias.isEmpty() ? field.name : alias);
                }
            }
            return Map.copyOf(fields);
        }
    };

    private ShadowProcessor() {}

    static void validate(ClassNode target, Class<?> mixin) {
        Map<String, String> shadows = SHADOWS.get(mixin);
        for (FieldNode declaration : MixinMetadata.read(mixin).fields) {
            String targetName = shadows.get(declaration.name);
            if (targetName == null) {
                continue;
            }
            FieldNode field = targetField(target, targetName);
            if (!field.desc.equals(declaration.desc)
                    || (field.access & Opcodes.ACC_STATIC) != (declaration.access & Opcodes.ACC_STATIC)) {
                throw new IllegalArgumentException("Shadow field type/static mismatch: " + declaration.name);
            }
            if (declaration.value != null) {
                throw new IllegalArgumentException("Shadow fields cannot declare constant initializers: " + declaration.name);
            }
        }
        validateInitializers(mixin, shadows);
    }

    static boolean usesShadow(MethodNode callback, Class<?> mixin) {
        String owner = Type.getInternalName(mixin);
        Map<String, String> shadows = SHADOWS.get(mixin);
        for (var instruction : callback.instructions) {
            if (instruction instanceof FieldInsnNode field && field.owner.equals(owner) && shadows.containsKey(field.name)) {
                return true;
            }
        }
        return false;
    }

    static void copyBody(ClassNode target, MethodNode destination, MethodNode callback, Class<?> mixin) {
        String owner = Type.getInternalName(mixin);
        Map<String, String> shadows = SHADOWS.get(mixin);
        validateCopiedBody(target, callback, mixin, shadows);

        Remapper remapper = new Remapper(Opcodes.ASM9) {
            @Override
            public String map(String name) {
                return name.equals(owner) ? target.name : name;
            }

            @Override
            public String mapFieldName(String fieldOwner, String name, String descriptor) {
                return fieldOwner.equals(owner) ? shadows.getOrDefault(name, name) : name;
            }
        };
        // Visiting into a fresh node clones labels, frames and handlers as well as instructions.
        MethodNode body = new MethodNode();
        callback.accept(new MethodRemapper(body, remapper));
        destination.instructions = body.instructions;
        destination.tryCatchBlocks = body.tryCatchBlocks;
        destination.localVariables = body.localVariables;
        destination.visibleLocalVariableAnnotations = body.visibleLocalVariableAnnotations;
        destination.invisibleLocalVariableAnnotations = body.invisibleLocalVariableAnnotations;
        destination.maxLocals = body.maxLocals;
        destination.maxStack = body.maxStack;
    }

    private static void validateInitializers(Class<?> mixin, Map<String, String> shadows) {
        String owner = Type.getInternalName(mixin);
        for (MethodNode method : MixinMetadata.read(mixin).methods) {
            if (!method.name.startsWith("<")) {
                continue;
            }
            for (var instruction : method.instructions) {
                if (instruction instanceof FieldInsnNode field && field.owner.equals(owner)
                        && shadows.containsKey(field.name) && isWrite(field)) {
                    throw new IllegalArgumentException("Shadow fields cannot have initializers: " + field.name);
                }
            }
        }
    }

    private static void validateCopiedBody(ClassNode target, MethodNode callback, Class<?> mixin,
                                           Map<String, String> shadows) {
        String owner = Type.getInternalName(mixin);
        if (!"java/lang/Object".equals(MixinMetadata.read(mixin).superName)) {
            throw new IllegalArgumentException("Copied replacements require a mixin extending Object directly");
        }
        for (var instruction : callback.instructions) {
            if (instruction instanceof FieldInsnNode field && field.owner.equals(owner)) {
                validateFieldAccess(target, field, shadows);
            }
            if (instruction instanceof MethodInsnNode call
                    && (call.owner.equals(owner) || referencesMixin(call.desc, owner))) {
                throw new IllegalArgumentException("Copied replacements cannot call mixin helpers or constructors: " + call.name);
            }
            if (instruction instanceof TypeInsnNode type && type.desc.equals(owner)) {
                throw new IllegalArgumentException("Copied replacements cannot construct or cast to the mixin class");
            }
            if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                rejectMixinConstant(dynamic.bsm, owner);
                for (Object argument : dynamic.bsmArgs) {
                    rejectMixinConstant(argument, owner);
                }
            }
            if (instruction instanceof LdcInsnNode constant) {
                rejectMixinConstant(constant.cst, owner);
            }
        }
    }

    private static void validateFieldAccess(ClassNode target, FieldInsnNode field, Map<String, String> shadows) {
        String targetName = shadows.get(field.name);
        if (targetName == null) {
            throw new IllegalArgumentException("Copied replacements may only access @Shadow mixin fields: " + field.name);
        }
        FieldNode real = targetField(target, targetName);
        if (isWrite(field) && (real.access & Opcodes.ACC_FINAL) != 0) {
            throw new IllegalArgumentException("Cannot write final shadow field: " + real.name);
        }
    }

    private static FieldNode targetField(ClassNode target, String name) {
        return target.fields.stream().filter(field -> field.name.equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Shadow field not found: " + target.name + "." + name));
    }

    private static boolean isWrite(FieldInsnNode field) {
        return field.getOpcode() == Opcodes.PUTFIELD || field.getOpcode() == Opcodes.PUTSTATIC;
    }

    private static boolean referencesMixin(String descriptor, String owner) {
        return descriptor.contains("L" + owner + ";");
    }

    private static void rejectMixinConstant(Object value, String owner) {
        if (value instanceof Handle handle
                && (handle.getOwner().equals(owner) || referencesMixin(handle.getDesc(), owner))) {
            throw new IllegalArgumentException("Copied replacements cannot use mixin lambdas or method references");
        }
        if (value instanceof Type type && referencesMixin(type.getDescriptor(), owner)) {
            throw new IllegalArgumentException("Copied replacements cannot expose the mixin type");
        }
        if (value instanceof ConstantDynamic dynamic) {
            rejectMixinConstant(dynamic.getBootstrapMethod(), owner);
            for (int i = 0; i < dynamic.getBootstrapMethodArgumentCount(); i++) {
                rejectMixinConstant(dynamic.getBootstrapMethodArgument(i), owner);
            }
        }
    }
}
