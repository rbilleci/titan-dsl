package io.titan.introspect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SqlStatementSplitterTest {

    @Test
    void splitsOnTopLevelSemicolonsOnly() {
        List<String> statements = SqlStatementSplitter.split("""
                CREATE TABLE a (id int);
                INSERT INTO a VALUES ('semi;colon');
                CREATE TABLE b (note varchar(10) DEFAULT 'x;y');
                """);
        assertEquals(3, statements.size());
        assertTrue(statements.get(1).contains("'semi;colon'"));
    }

    @Test
    void commentsNeverSplitStatementsAndCommentOnlyChunksAreDropped() {
        List<String> statements = SqlStatementSplitter.split("""
                -- leading comment; with a semicolon
                CREATE TABLE a (
                  id int -- trailing; comment
                );

                /* block; comment */
                ;
                # mysql comment; with semicolon
                CREATE TABLE b (id int);
                """);
        assertEquals(2, statements.size());
        assertTrue(statements.get(0).startsWith("CREATE TABLE a"));
        assertTrue(statements.get(1).startsWith("CREATE TABLE b"));
    }

    @Test
    void dollarQuotedBodiesStayInOneStatement() {
        List<String> statements = SqlStatementSplitter.split("""
                CREATE FUNCTION f() RETURNS void AS $body$
                BEGIN
                    PERFORM 1;
                END;
                $body$ LANGUAGE plpgsql;
                CREATE TABLE t (id int);
                """);
        assertEquals(2, statements.size());
        assertTrue(statements.get(0).contains("PERFORM 1;"));
    }

    @Test
    void backtickAndDoubleQuotedIdentifiersAreHonored() {
        List<String> statements = SqlStatementSplitter.split(
                "CREATE TABLE `weird;name` (id int); CREATE TABLE \"also;weird\" (id int);");
        assertEquals(2, statements.size());
    }

    @Test
    void stripLeadingCommentsKeepsTheStatementText() {
        assertEquals("CREATE TABLE t (id int)",
                SqlStatementSplitter.stripLeadingComments("-- hi\n/* there */ CREATE TABLE t (id int)").trim());
    }
}
