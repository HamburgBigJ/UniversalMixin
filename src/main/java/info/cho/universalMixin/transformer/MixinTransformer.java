package info.cho.universalMixin.transformer;

import info.cho.universalMixin.discovery.MixinRegistry;
import info.cho.universalMixin.metadata.AccessRules;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Applies discovered mixins when the JVM first defines a matching target class. */
public final class MixinTransformer implements ClassFileTransformer {
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer)
            throws IllegalClassFormatException {
        if (className == null) {
            return null;
        }
        List<Class<?>> mixins = MixinRegistry.getMixins(className);
        Set<String> publicClasses = MixinRegistry.getPublicClassTargets();
        if (mixins.isEmpty() && publicClasses.isEmpty()) {
            return null;
        }
        try {
            byte[] result = transform(classfileBuffer, mixins, publicClasses);
            return result == classfileBuffer ? null : result;
        } catch (RuntimeException exception) {
            // Instrumentation ignores transformer exceptions and loads the original bytes.
            // Report the failure explicitly so a broken mixin is not silently missed.
            System.err.println("[UniversalMixin] Failed to transform " + className + ": " + exception);
            IllegalClassFormatException failure = new IllegalClassFormatException(exception.getMessage());
            failure.initCause(exception);
            throw failure;
        }
    }

    public static byte[] transform(byte[] classBytes, Class<?> mixinClass) {
        return transform(classBytes, List.of(mixinClass));
    }

    public static byte[] transform(byte[] classBytes, List<Class<?>> mixins) {
        return transform(classBytes, mixins, Set.of());
    }

    public static byte[] transform(byte[] classBytes, List<Class<?>> mixins, Set<String> publicClasses) {
        if (mixins.isEmpty() && publicClasses.isEmpty()) {
            return classBytes;
        }
        ClassNode target = new ClassNode();
        new ClassReader(classBytes).accept(target, 0);
        Set<String> widened = new HashSet<>(publicClasses);
        for (Class<?> mixin : mixins) {
            if (AccessRules.of(mixin).type()) {
                widened.add(target.name);
            }
            AccessProcessor.apply(target, mixin);
        }
        boolean metadataChanged = AccessProcessor.widenClassMetadata(target, widened);
        if (mixins.isEmpty() && !metadataChanged) {
            return classBytes;
        }
        Set<String> replaced = new HashSet<>();
        for (Class<?> mixin : mixins) {
            ReplacementProcessor.apply(target, mixin, replaced);
        }
        // Inject after replacements, so HEAD/RETURN hooks wrap the new body too.
        for (Class<?> mixin : mixins) {
            InjectionProcessor.apply(target, mixin);
        }

        // Injections preserve frames; copied replacements already carry remapped frames.
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        target.accept(writer);
        return writer.toByteArray();
    }
}
