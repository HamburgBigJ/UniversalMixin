package info.cho.universalMixin.agent;

import info.cho.universalMixin.discovery.MixinRegistry;
import info.cho.universalMixin.transformer.MixinTransformer;

import java.io.IOException;
import java.lang.instrument.Instrumentation;

/** Java agent entry point, invoked by the JVM before the application starts. */
public final class MixinAgent {
    private MixinAgent() {}

    public static void premain(String args, Instrumentation instrumentation) throws IOException {
        var modJars = ModJarLoader.append(args, instrumentation);
        MixinRegistry.discover(modJars);
        instrumentation.addTransformer(new MixinTransformer());
    }
}
