package titan.dsl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a static Java method as a Titan stored procedure entry point. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface StoredProcedure {
}
