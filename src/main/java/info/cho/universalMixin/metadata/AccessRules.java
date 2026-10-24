package info.cho.universalMixin.metadata;

import info.cho.universalMixin.api.WidenAccess;

import java.util.List;

/** Parsed access changes, shared by discovery and transformation. */
public record AccessRules(boolean type, List<String> fields, List<String> methods) {
    private static final AccessRules NONE = new AccessRules(false, List.of(), List.of());
    private static final ClassValue<AccessRules> CACHE = new ClassValue<>() {
        @Override
        protected AccessRules computeValue(Class<?> mixin) {
            var annotation = MixinMetadata.annotation(MixinMetadata.read(mixin), WidenAccess.class);
            if (annotation == null) {
                return NONE;
            }
            return new AccessRules(
                    (Boolean) annotation.getOrDefault("type", false),
                    strings(annotation.getOrDefault("fields", List.of())),
                    strings(annotation.getOrDefault("methods", List.of())));
        }
    };

    public AccessRules {
        fields = List.copyOf(fields);
        methods = List.copyOf(methods);
    }

    public static AccessRules of(Class<?> mixin) {
        return CACHE.get(mixin);
    }

    private static List<String> strings(Object value) {
        return ((List<?>) value).stream().map(String.class::cast).toList();
    }
}
