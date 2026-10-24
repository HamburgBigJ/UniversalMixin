package info.cho.universalMixin.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mirrors an existing target field for direct access from a copied replacement body.
 * The field name (or value alias), type and static flag must match the target.
 * This declaration does not add or initialize a field in the target.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Shadow {
    String value() default "";
}
