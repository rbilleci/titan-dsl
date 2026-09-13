package titan.dsl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Carries the physical SQL column name for generated catalog fields so transpilation does not
 * depend on source-tree availability.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface PhysicalColumn {

    String value();

}
