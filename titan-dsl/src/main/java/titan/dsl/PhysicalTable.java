package titan.dsl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Carries physical SQL table metadata for generated catalog descriptors so transpilation can
 * recover deployable table names from compiled symbols.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface PhysicalTable {

    String name();

    String schema() default "";

}
