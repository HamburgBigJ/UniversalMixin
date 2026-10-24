package info.cho.universalMixin.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Calls a public static, no-argument void method at the selected injection point.
 * The method name matches every concrete overload; constructors are not supported.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Inject {

    String method();

    At at() default At.HEAD;
}
