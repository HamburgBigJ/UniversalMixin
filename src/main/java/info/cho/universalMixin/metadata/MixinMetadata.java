package info.cho.universalMixin.metadata;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reads declarations from bytecode so target classes are not loaded during discovery. */
public final class MixinMetadata {
    private static final ClassValue<ClassNode> CLASSES = new ClassValue<>() {
        @Override
        protected ClassNode computeValue(Class<?> mixin) {
            return readClass(mixin);
        }
    };

    private MixinMetadata() {}

    public static ClassNode read(Class<?> mixin) {
        return CLASSES.get(mixin);
    }

    public static Map<String, Object> annotation(ClassNode type, Class<? extends Annotation> annotation) {
        return findAnnotation(type.visibleAnnotations, annotation);
    }

    public static Map<String, Object> annotation(MethodNode method, Class<? extends Annotation> annotation) {
        return findAnnotation(method.visibleAnnotations, annotation);
    }

    public static Map<String, Object> annotation(FieldNode field, Class<? extends Annotation> annotation) {
        return findAnnotation(field.visibleAnnotations, annotation);
    }

    private static ClassNode readClass(Class<?> mixin) {
        String resource = "/" + Type.getInternalName(mixin) + ".class";
        try (var stream = mixin.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalArgumentException("Missing mixin bytecode: " + resource);
            }
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot read mixin bytecode: " + resource, exception);
        }
    }

    private static Map<String, Object> findAnnotation(List<AnnotationNode> annotations,
                                                      Class<? extends Annotation> type) {
        if (annotations == null) {
            return null;
        }
        String descriptor = Type.getDescriptor(type);
        for (AnnotationNode annotation : annotations) {
            if (annotation.desc.equals(descriptor)) {
                return values(annotation);
            }
        }
        return null;
    }

    private static Map<String, Object> values(AnnotationNode annotation) {
        Map<String, Object> values = new HashMap<>();
        if (annotation.values != null) {
            for (int i = 0; i < annotation.values.size(); i += 2) {
                values.put((String) annotation.values.get(i), annotation.values.get(i + 1));
            }
        }
        return Map.copyOf(values);
    }
}
