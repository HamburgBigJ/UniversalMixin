package info.cho.universalMixin.transformer;

import info.cho.universalMixin.api.At;
import info.cho.universalMixin.api.Inject;
import info.cho.universalMixin.api.Replace;
import info.cho.universalMixin.api.Shadow;
import info.cho.universalMixin.api.WidenAccess;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ReplacementAndAccessTest {
    public static final List<String> EVENTS = new ArrayList<>();

    @Test
    void replacesInstanceBodyAndWrapsReplacementWithInjectionsAcrossMixins() throws Exception {
        EVENTS.clear();
        // Deliberately supply injection before replacement to test phase ordering.
        Class<?> target = load(Hooks.class, InstanceReplacement.class);
        Object instance = target.getConstructor().newInstance();
        assertEquals(19L, target.getMethod("compute", long.class, double.class).invoke(instance, 3L, 2.0));
        assertEquals(List.of("head", "replacement", "return"), EVENTS);
        assertEquals(13, target.getField("health").getInt(instance));
        assertEquals("original overload", target.getMethod("compute", String.class).invoke(instance, "text"));
    }

    @Test
    void delegatesStaticWideArgumentsVoidAndReferenceReturns() throws Exception {
        EVENTS.clear();
        Class<?> target = load(StaticReplacement.class);
        assertEquals(6.5, target.getMethod("calculate", long.class, double.class).invoke(null, 4L, 2.5));
        target.getMethod("notify", String.class).invoke(null, "hello");
        assertEquals(List.of("hello"), EVENTS);
        String[] values = {"one", "two"};
        assertSame(values, target.getMethod("identity", String[].class).invoke(null, (Object) values));
    }

    @Test
    void widensPrivateAndProtectedMembersWithoutRemovingFinal() throws Exception {
        Class<?> target = load(AccessRules.class);
        Object instance = target.getConstructor().newInstance();
        var health = target.getField("health");
        health.setInt(instance, 8);
        assertEquals(8, health.getInt(instance));
        assertEquals("protected", target.getField("label").get(instance));
        assertTrue(Modifier.isFinal(target.getField("LIMIT").getModifiers()));
        assertEquals(9, target.getMethod("hidden", int.class).invoke(instance, 1));
        assertFalse(Modifier.isPublic(target.getDeclaredField("untouched").getModifiers()));
    }

    @Test
    void updatesNestedClassMetadataAndPreservesUnrelatedFlags() {
        ClassNode outer = new ClassNode();
        outer.name = "sample/Outer";
        var inner = new InnerClassNode("sample/Outer$Inner", "sample/Outer", "Inner",
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL);
        outer.innerClasses.add(inner);
        assertTrue(AccessProcessor.widenClassMetadata(outer, Set.of(inner.name)));
        assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, inner.access);
        assertFalse(AccessProcessor.widenClassMetadata(outer, Set.of(inner.name)));
        ClassNode type = new ClassNode();
        type.name = inner.name;
        type.access = Opcodes.ACC_FINAL | Opcodes.ACC_SUPER;
        assertTrue(AccessProcessor.widenClassMetadata(type, Set.of(inner.name)));
        assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER, type.access);
    }

    @Test
    void rejectsBadSignaturesMissingTargetsAndConflictingReplacements() {
        assertThrows(IllegalArgumentException.class, () -> load(BadSignature.class));
        assertThrows(IllegalArgumentException.class, () -> load(NonStatic.class));
        assertThrows(IllegalArgumentException.class, () -> load(MissingReplacement.class));
        assertThrows(IllegalArgumentException.class, () -> load(CombinedAnnotations.class));
        assertThrows(IllegalArgumentException.class, () -> load(InstanceReplacement.class, InstanceReplacement.class));
        assertThrows(IllegalArgumentException.class, () -> load(MissingField.class));
        assertThrows(IllegalArgumentException.class, () -> load(MissingMethod.class));
    }

    @Test
    void rejectsAbstractNativeAndConstructorReplacements() {
        for (int flags : new int[]{Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_NATIVE}) {
            ClassNode target = new ClassNode();
            target.name = "sample/Target";
            target.methods.add(new MethodNode(flags, "compute", "(JD)J", null, null));
            assertThrows(IllegalArgumentException.class,
                    () -> ReplacementProcessor.apply(target, InstanceReplacement.class, new HashSet<>()));
        }
        ClassNode target = new ClassNode();
        target.name = "sample/Target";
        target.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> ReplacementProcessor.apply(target, ConstructorReplacement.class, new HashSet<>()));
    }

    @Test
    void copiedInstanceReplacementReadsAndWritesRealPrivateFieldsWithBranchesAndHandlers() throws Exception {
        Class<?> target = load(DirectFields.class);
        Object first = target.getConstructor().newInstance();
        Object second = target.getConstructor().newInstance();
        assertEquals(17, target.getMethod("decrement", int.class).invoke(first, 3));
        assertEquals(17, target.getMethod("decrement", int.class).invoke(first, -1));
        assertEquals(18, target.getMethod("decrement", int.class).invoke(second, 2));
        assertEquals(17, target.getMethod("decrement", int.class).invoke(first, 0));
        assertEquals("protected:17", target.getMethod("healthText").invoke(first));
        assertTrue(Modifier.isPrivate(target.getDeclaredField("health").getModifiers()));
        assertTrue(Modifier.isProtected(target.getDeclaredField("label").getModifiers()));
    }

    @Test
    void copiedStaticReplacementsBindStaticShadowAliasesAndReadFinalFields() throws Exception {
        Class<?> target = load(DirectStatics.class);
        assertEquals(21.5, target.getMethod("calculate", long.class, double.class).invoke(null, 1L, 0.5));
        assertEquals(21.0, target.getMethod("calculate", long.class, double.class).invoke(null, 0L, 0.0));
    }

    @Test
    void rejectsInvalidShadowsUnsupportedHelpersAndWritesToFinalFields() {
        assertThrows(IllegalArgumentException.class, () -> load(MissingShadow.class));
        assertThrows(IllegalArgumentException.class, () -> load(WrongShadowType.class));
        assertThrows(IllegalArgumentException.class, () -> load(WrongShadowStatic.class));
        assertThrows(IllegalArgumentException.class, () -> load(InitializedShadow.class));
        assertThrows(IllegalArgumentException.class, () -> load(HelperCall.class));
        assertThrows(IllegalArgumentException.class, () -> load(UnmarkedField.class));
        assertThrows(IllegalArgumentException.class, () -> load(FinalWrite.class));
        assertThrows(IllegalArgumentException.class, () -> load(ShadowInjection.class));
        assertThrows(IllegalArgumentException.class, () -> load(ShadowLambda.class));
    }

    private static Class<?> load(Class<?>... mixins) throws IOException {
        String resource = "/" + Target.class.getName().replace('.', '/') + ".class";
        byte[] bytes;
        try (var stream = Target.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException("Missing fixture " + resource);
            }
            bytes = MixinTransformer.transform(stream.readAllBytes(), List.of(mixins));
        }
        return new ClassLoader(ReplacementAndAccessTest.class.getClassLoader()) {
            Class<?> defineTarget() {
                return defineClass(Target.class.getName(), bytes, 0, bytes.length);
            }
        }.defineTarget();
    }

    public static class Target {
        private int health = 20;
        protected String label = "protected";
        private static final long LIMIT = 20;
        private int untouched;
        private static long counter = 20;

        public int decrement(int amount) {
            throw new AssertionError("original");
        }
        public String healthText() {
            return "original";
        }

        private int hidden(int amount) {
            return health + amount;
        }

        public long compute(long amount, double scale) {
            try {
                EVENTS.add("original");
                return (long) (amount * scale);
            } catch (RuntimeException failure) {
                return -1;
            }
        }

        public String compute(String text) {
            return "original overload";
        }
        public static double calculate(long value, double extra) {
            return -1;
        }
        public static void notify(String message) {
            throw new AssertionError("original body");
        }
        public static String[] identity(String[] values) {
            return null;
        }
    }

    @WidenAccess(fields = {"health"})
    public static class InstanceReplacement {
        @Replace(method = "compute", descriptor = "(JD)J")
        public static long compute(Object receiver, long value, double scale) throws ReflectiveOperationException {
            EVENTS.add("replacement");
            var field = receiver.getClass().getField("health");
            field.setInt(receiver, 13);
            return field.getInt(receiver) + (long) (value * scale);
        }
    }

    public static class Hooks {
        @Inject(method = "compute")
        public static void head() {
            EVENTS.add("head");
        }
        @Inject(method = "compute", at = At.RETURN)
        public static void tail() {
            EVENTS.add("return");
        }
    }

    public interface StaticReplacement {
        @Replace(method = "calculate")
        static double calculate(long value, double extra) {
            return value + extra;
        }
        @Replace(method = "notify")
        static void notify(String message) {
            EVENTS.add(message);
        }
        @Replace(method = "identity")
        static String[] identity(String[] values) {
            return values;
        }
    }

    public static class DirectFields {
        @Shadow
        private int health;
        @Shadow("label")
        private String name;

        @Replace(method = "decrement")
        public int decrement(int amount) {
            try {
                if (amount < 0) {
                    throw new IllegalArgumentException("negative");
                }
                health -= amount;
            } catch (IllegalArgumentException expected) {
                return health;
            }
            return health;
        }

        @Replace(method = "healthText")
        public String healthText() {
            return name + ":" + health;
        }
    }

    public static class DirectStatics {
        @Shadow("counter")
        private static long count;
        @Shadow("LIMIT")
        private static long limit;
        @Replace(method = "calculate")
        public static double calculate(long value, double extra) {
            count++;
            return count + extra + value - (limit - 19);
        }
    }

    public static class MissingShadow {
        @Shadow private int absent;
    }
    public static class WrongShadowType {
        @Shadow private String health;
    }
    public static class WrongShadowStatic {
        @Shadow private static int health;
    }
    public static class InitializedShadow {
        @Shadow private int health = 1;
    }
    public static class HelperCall {
        @Shadow
        private int health;
        private int helper() {
            return health;
        }
        @Replace(method = "decrement")
        public int decrement(int amount) {
            return helper();
        }
    }
    public static class UnmarkedField {
        private int health;
        @Replace(method = "decrement")
        public int decrement(int amount) {
            return health;
        }
    }
    public static class FinalWrite {
        @Shadow("LIMIT")
        private static long limit;
        @Replace(method = "calculate")
        public static double calculate(long value, double extra) {
            limit = value;
            return extra;
        }
    }
    public static class ShadowInjection {
        @Shadow("counter")
        private static long count;
        @Inject(method = "calculate")
        public static void inject() {
            count++;
        }
    }
    public static class ShadowLambda {
        @Shadow
        private int health;
        @Replace(method = "decrement")
        public int decrement(int amount) {
            java.util.function.IntSupplier supplier = () -> health;
            return supplier.getAsInt();
        }
    }

    @WidenAccess(fields = {"health", "label", "LIMIT"}, methods = {"hidden"})
    public static class AccessRules {}
    @WidenAccess(fields = {"absent"})
    public static class MissingField {}
    @WidenAccess(methods = {"absent"})
    public static class MissingMethod {}

    public static class BadSignature {
        @Replace(method = "calculate")
        public static int calculate(long value, double extra) {
            return 0;
        }
    }
    public static class NonStatic {
        @Replace(method = "calculate")
        public double calculate(long value, double extra) {
            return 0;
        }
    }
    public static class MissingReplacement {
        @Replace(method = "absent")
        public static void absent() {}
    }
    public static class CombinedAnnotations {
        @Inject(method = "calculate")
        @Replace(method = "calculate")
        public static double calculate(long value, double extra) {
            return 0;
        }
    }
    public static class ConstructorReplacement {
        @Replace(method = "<init>")
        public static void constructor(Object receiver) {}
    }
}
