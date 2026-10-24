package info.cho.universalMixin.transformer;

import info.cho.universalMixin.api.At;
import info.cho.universalMixin.api.Inject;
import info.cho.universalMixin.api.Mixin;
import info.cho.universalMixin.discovery.MixinRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MixinTransformerTest {
    public static final List<String> EVENTS = new ArrayList<>();

    @BeforeEach
    void resetEvents() {
        EVENTS.clear();
    }

    @Test
    void runsHeadAndReturnAtEveryNormalExitAndPreservesWideValues() throws Exception {
        Class<?> target = transform(Callbacks.class);
        assertEquals(42L, target.getMethod("value", boolean.class).invoke(null, true));
        assertEquals(List.of("head", "body", "return"), EVENTS);
        EVENTS.clear();
        assertEquals(-1L, target.getMethod("value", boolean.class).invoke(null, false));
        assertEquals(List.of("head", "body", "return"), EVENTS);
    }

    @Test
    void returnCallbackDoesNotRunWhenTargetThrows() throws Exception {
        Class<?> target = transform(ThrowCallbacks.class);
        InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                () -> target.getMethod("fail").invoke(null));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertEquals(List.of("head"), EVENTS);
    }

    @Test
    void supportsStaticInterfaceCallbacks() throws Exception {
        transform(InterfaceCallback.class).getMethod("value", boolean.class).invoke(null, true);
        assertEquals(List.of("interface", "body"), EVENTS);
    }

    @Test
    void rejectsInvalidCallbacksAndMissingMethods() {
        assertThrows(IllegalArgumentException.class, () -> transform(InstanceCallback.class));
        assertThrows(IllegalArgumentException.class, () -> transform(ParameterCallback.class));
        assertThrows(IllegalArgumentException.class, () -> transform(ReturningCallback.class));
        assertThrows(IllegalArgumentException.class, () -> transform(MissingCallback.class));
    }

    @Test
    void leavesUnmatchedClassesAlone() throws Exception {
        byte[] bytes = originalBytes();
        assertSame(bytes, MixinTransformer.transform(bytes, List.of()));
        assertNull(new MixinTransformer().transform(null, null, null, null, bytes));
        assertNull(new MixinTransformer().transform(null, "unmatched/Target", null, null, bytes));
    }

    @Test
    void discoversNamedTargetsAndPublishesImmutableLists() {
        MixinRegistry.discover();
        List<Class<?>> mixins = MixinRegistry.getMixins("sample/NamedTarget");
        assertEquals(List.of(NamedMixin.class), mixins);
        assertThrows(UnsupportedOperationException.class, () -> mixins.add(Callbacks.class));
        MixinRegistry.discover();
        assertEquals(mixins, MixinRegistry.getMixins("sample/NamedTarget"));
    }

    private static byte[] originalBytes() throws IOException {
        String resource = "/" + Target.class.getName().replace('.', '/') + ".class";
        try (var stream = Target.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException("Missing fixture: " + resource);
            }
            return stream.readAllBytes();
        }
    }

    private static Class<?> transform(Class<?> mixin) throws IOException {
        byte[] bytes = MixinTransformer.transform(originalBytes(), mixin);
        return new ClassLoader(MixinTransformerTest.class.getClassLoader()) {
            Class<?> defineTarget() {
                return defineClass(Target.class.getName(), bytes, 0, bytes.length);
            }
        }.defineTarget();
    }

    public static class Target {
        public static long value(boolean positive) {
            EVENTS.add("body");
            if (positive) {
                return 42L;
            }
            return -1L;
        }

        public static void fail() {
            throw new IllegalStateException("expected");
        }
    }

    public static class Callbacks {
        @Inject(method = "value")
        public static void head() {
            EVENTS.add("head");
        }

        @Inject(method = "value", at = At.RETURN)
        public static void tail() {
            EVENTS.add("return");
        }
    }

    public static class ThrowCallbacks {
        @Inject(method = "fail")
        public static void head() {
            EVENTS.add("head");
        }

        @Inject(method = "fail", at = At.RETURN)
        public static void tail() {
            EVENTS.add("return");
        }
    }

    public interface InterfaceCallback {
        @Inject(method = "value")
        static void head() {
            EVENTS.add("interface");
        }
    }

    public static class InstanceCallback {
        @Inject(method = "value")
        public void callback() {}
    }

    public static class ParameterCallback {
        @Inject(method = "value")
        public static void callback(int value) {}
    }

    public static class ReturningCallback {
        @Inject(method = "value")
        public static int callback() {
            return 0;
        }
    }

    public static class MissingCallback {
        @Inject(method = "absent")
        public static void callback() {}
    }

    @Mixin(target = "sample.NamedTarget")
    public static class NamedMixin {}
}
