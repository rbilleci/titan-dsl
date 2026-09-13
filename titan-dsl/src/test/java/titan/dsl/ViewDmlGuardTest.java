package titan.dsl;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ViewDmlGuardTest {

    @Test
    void viewIsNotATableTypeForDmlApis() throws Exception {
        assertFalse(Table.class.isAssignableFrom(View.class));

        Method insertInto = DSL.class.getMethod("insertInto", Table.class);
        Method update = DSL.class.getMethod("update", Table.class);
        Method deleteFrom = DSL.class.getMethod("deleteFrom", Table.class);

        assertEquals(Table.class, insertInto.getParameterTypes()[0]);
        assertEquals(Table.class, update.getParameterTypes()[0]);
        assertEquals(Table.class, deleteFrom.getParameterTypes()[0]);
    }
}
