package info.cho.universalMixin.transformer;

import info.cho.universalMixin.metadata.AccessRules;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.util.Set;

/** Updates member flags and nested-class metadata without removing final flags. */
final class AccessProcessor {
    private AccessProcessor() {}

    static void apply(ClassNode target, Class<?> mixin) {
        AccessRules access = AccessRules.of(mixin);
        if (access.type()) {
            target.access = makePublic(target.access);
        }
        for (String name : access.fields()) {
            boolean found = false;
            for (var field : target.fields) {
                if (field.name.equals(name)) {
                    field.access = makePublic(field.access);
                    found = true;
                }
            }
            if (!found) {
                throw new IllegalArgumentException("Field not found: " + target.name + "." + name);
            }
        }
        for (String name : access.methods()) {
            boolean found = false;
            for (var method : target.methods) {
                if (method.name.equals(name) && !name.startsWith("<")) {
                    method.access = makePublic(method.access);
                    found = true;
                }
            }
            if (!found) {
                throw new IllegalArgumentException("Method not found: " + target.name + "." + name);
            }
        }
    }

    /** Referencing classes also carry access flags for nested classes in InnerClasses. */
    static boolean widenClassMetadata(ClassNode target, Set<String> publicClasses) {
        boolean changed = false;
        if (publicClasses.contains(target.name)) {
            int access = makePublic(target.access);
            changed = access != target.access;
            target.access = access;
        }
        for (var inner : target.innerClasses) {
            if (publicClasses.contains(inner.name)) {
                int access = makePublic(inner.access);
                changed |= access != inner.access;
                inner.access = access;
            }
        }
        return changed;
    }

    private static int makePublic(int access) {
        return (access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
    }
}
