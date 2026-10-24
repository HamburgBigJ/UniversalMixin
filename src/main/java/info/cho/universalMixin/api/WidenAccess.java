package info.cho.universalMixin.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Makes selected members of the mixin target public, retaining final/static flags. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface WidenAccess {
    boolean type() default false;
    String[] fields() default {};
    /** Method names; all overloads with a matching name become public. */
    String[] methods() default {};
}
