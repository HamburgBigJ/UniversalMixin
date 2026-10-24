package info.cho.universalMixin.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Replaces a concrete target method. Instance callbacks have the same signature
 * as the instance target and are copied into it, allowing direct @Shadow access.
 * Static callbacks are delegated to (or copied if using shadows); instance targets
 * then require a leading receiver parameter typed as Object or the target class.
 * Descriptor optionally selects an overload.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Replace {
    String method();
    String descriptor() default "";
}
