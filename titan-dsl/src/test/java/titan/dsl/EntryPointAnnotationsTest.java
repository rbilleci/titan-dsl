package titan.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EntryPointAnnotationsTest {

    @Test
    void storedProcedureHasRuntimeRetentionAndMethodTarget() {
        Retention retention = StoredProcedure.class.getAnnotation(Retention.class);
        Target target = StoredProcedure.class.getAnnotation(Target.class);

        assertNotNull(retention);
        assertEquals(RetentionPolicy.RUNTIME, retention.value());
        assertNotNull(target);
        assertEquals(Set.of(ElementType.METHOD), Set.copyOf(Arrays.asList(target.value())));
    }

    @Test
    void storedFunctionHasRuntimeRetentionAndMethodTarget() {
        Retention retention = StoredFunction.class.getAnnotation(Retention.class);
        Target target = StoredFunction.class.getAnnotation(Target.class);

        assertNotNull(retention);
        assertEquals(RetentionPolicy.RUNTIME, retention.value());
        assertNotNull(target);
        assertEquals(Set.of(ElementType.METHOD), Set.copyOf(Arrays.asList(target.value())));
    }

    @Test
    void sqlAnnotationExposesDialectAndValueAttributes() throws NoSuchMethodException {
        Retention retention = SQL.class.getAnnotation(Retention.class);
        Target target = SQL.class.getAnnotation(Target.class);
        Method dialectAttr = SQL.class.getDeclaredMethod("dialect");
        Method valueAttr = SQL.class.getDeclaredMethod("value");

        assertNotNull(retention);
        assertEquals(RetentionPolicy.RUNTIME, retention.value());
        assertNotNull(target);
        assertEquals(
                Set.of(ElementType.METHOD, ElementType.LOCAL_VARIABLE),
                Set.copyOf(Arrays.asList(target.value())));
        assertEquals(SqlDialect.class, dialectAttr.getReturnType());
        assertEquals(String.class, valueAttr.getReturnType());
    }

    @Test
    void triggerAnnotationExposesContractAttributes() throws NoSuchMethodException {
        Retention retention = Trigger.class.getAnnotation(Retention.class);
        Target target = Trigger.class.getAnnotation(Target.class);
        Method tableAttr = Trigger.class.getDeclaredMethod("table");
        Method timingAttr = Trigger.class.getDeclaredMethod("timing");
        Method eventAttr = Trigger.class.getDeclaredMethod("event");
        Method forEachAttr = Trigger.class.getDeclaredMethod("forEach");

        assertNotNull(retention);
        assertEquals(RetentionPolicy.RUNTIME, retention.value());
        assertNotNull(target);
        assertEquals(Set.of(ElementType.METHOD), Set.copyOf(Arrays.asList(target.value())));
        assertEquals(String.class, tableAttr.getReturnType());
        assertEquals(TriggerTiming.class, timingAttr.getReturnType());
        assertEquals(TriggerEvent[].class, eventAttr.getReturnType());
        assertEquals(TriggerForEach.class, forEachAttr.getReturnType());
    }

    @Test
    void securityDefinerAnnotationHasRuntimeRetentionAndMethodTarget() {
        Retention retention = SecurityDefiner.class.getAnnotation(Retention.class);
        Target target = SecurityDefiner.class.getAnnotation(Target.class);

        assertNotNull(retention);
        assertEquals(RetentionPolicy.RUNTIME, retention.value());
        assertNotNull(target);
        assertEquals(Set.of(ElementType.METHOD), Set.copyOf(Arrays.asList(target.value())));
    }

    @Test
    void outAnnotationHasRuntimeRetentionAndParameterTarget() {
        Retention retention = Out.class.getAnnotation(Retention.class);
        Target target = Out.class.getAnnotation(Target.class);

        assertNotNull(retention);
        assertEquals(RetentionPolicy.RUNTIME, retention.value());
        assertNotNull(target);
        assertEquals(Set.of(ElementType.PARAMETER), Set.copyOf(Arrays.asList(target.value())));
    }

    @Test
    void inOutAnnotationHasRuntimeRetentionAndParameterTarget() {
        Retention retention = InOut.class.getAnnotation(Retention.class);
        Target target = InOut.class.getAnnotation(Target.class);

        assertNotNull(retention);
        assertEquals(RetentionPolicy.RUNTIME, retention.value());
        assertNotNull(target);
        assertEquals(Set.of(ElementType.PARAMETER), Set.copyOf(Arrays.asList(target.value())));
    }

    @Test
    void scheduledJobAnnotationExposesCronAndNameAttributes() throws NoSuchMethodException {
        Retention retention = ScheduledJob.class.getAnnotation(Retention.class);
        Target target = ScheduledJob.class.getAnnotation(Target.class);
        Method cronAttr = ScheduledJob.class.getDeclaredMethod("cron");
        Method nameAttr = ScheduledJob.class.getDeclaredMethod("name");

        assertNotNull(retention);
        assertEquals(RetentionPolicy.RUNTIME, retention.value());
        assertNotNull(target);
        assertEquals(Set.of(ElementType.METHOD), Set.copyOf(Arrays.asList(target.value())));
        assertEquals(String.class, cronAttr.getReturnType());
        assertEquals(String.class, nameAttr.getReturnType());
    }

    @Test
    void viewDefinitionAnnotationExposesNameAndSharedAttributes() throws NoSuchMethodException {
        Retention retention = ViewDefinition.class.getAnnotation(Retention.class);
        Target target = ViewDefinition.class.getAnnotation(Target.class);
        Method nameAttr = ViewDefinition.class.getDeclaredMethod("name");
        Method sharedAttr = ViewDefinition.class.getDeclaredMethod("shared");

        assertNotNull(retention);
        assertEquals(RetentionPolicy.RUNTIME, retention.value());
        assertNotNull(target);
        assertEquals(Set.of(ElementType.FIELD), Set.copyOf(Arrays.asList(target.value())));
        assertEquals(String.class, nameAttr.getReturnType());
        assertEquals(boolean.class, sharedAttr.getReturnType());
    }

    @Test
    void triggerHelpersAreGuardedOutsideTranspiledContext() {
        assertThrows(UnsupportedOperationException.class, DSL::newRow);
        assertThrows(UnsupportedOperationException.class, DSL::oldRow);
        assertThrows(IllegalStateException.class, () -> DSL.abortWithError("boom"));
    }

    @Test
    void oldRowContractIsReadOnlyWhileNewRowRemainsWritable() throws NoSuchMethodException {
        Method oldRowMethod = DSL.class.getDeclaredMethod("oldRow");
        Method newRowMethod = DSL.class.getDeclaredMethod("newRow");

        assertEquals(ReadOnlyTriggerRowAccessor.class, oldRowMethod.getReturnType());
        assertEquals(TriggerRowAccessor.class, newRowMethod.getReturnType());
    }
}
