package io.titan.introspect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regex-based DDL parser for CREATE TABLE / CREATE VIEW / CREATE INDEX statements.
 *
 * <p><strong>This is the documented no-Docker fallback</strong> for DDL-file introspection
 * (audit G-6/G-7). The supported path is container-backed introspection
 * ({@link DdlContainerIntrospector}), which applies the DDL to a scratch Testcontainers database
 * and introspects it through the real JDBC {@link SchemaIntrospector} — equivalent to the JDBC
 * path by construction.</p>
 *
 * <p>This parser never drops SQL silently: any statement (or CREATE TABLE clause) it does not
 * recognize raises {@link DdlParseException} naming the offending SQL and pointing at container
 * mode. What it genuinely handles:</p>
 *
 * <ul>
 *   <li>CREATE TABLE [IF NOT EXISTS] with column definitions, named/inline PRIMARY KEY /
 *       UNIQUE / FOREIGN KEY (including {@code ON DELETE}/{@code ON UPDATE} actions, which carry
 *       no schema metadata), KEY/INDEX clauses, and CHECK constraints (recognized and ignored —
 *       the JDBC introspector does not surface them either),</li>
 *   <li>CREATE VIEW (column names only — view columns stay untyped, a documented fidelity gap
 *       of this fallback),</li>
 *   <li>CREATE [UNIQUE] INDEX on plain column lists,</li>
 *   <li>MySQL inline {@code ENUM(...)} column types (synthesized type name
 *       {@code <table>_<column>_enum}, matching JDBC introspection),</li>
 *   <li>COMMENT ON statements (recognized and ignored).</li>
 * </ul>
 *
 * <p>Everything else — {@code ALTER TABLE}, {@code CREATE OR REPLACE VIEW}, {@code CREATE TYPE},
 * {@code CREATE SEQUENCE}, table options such as {@code ENGINE=InnoDB}, expression indexes — is
 * a hard error directing the build at container mode.</p>
 */
public final class DdlSchemaParser {

    private static final Pattern CREATE_TABLE_HEAD = Pattern.compile(
            "(?is)^create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?([a-zA-Z0-9_\\.\\\"`]+)\\s*\\("
    );
    private static final Pattern CREATE_VIEW_STATEMENT = Pattern.compile(
            "(?is)^create\\s+view\\s+([a-zA-Z0-9_\\.\\\"`]+)\\s+as\\s+select\\s+(.*?)\\s+from\\s+.*"
    );
    private static final Pattern CREATE_INDEX_STATEMENT = Pattern.compile(
            "(?is)^create\\s+(unique\\s+)?index(?:\\s+if\\s+not\\s+exists)?(?:\\s+([a-zA-Z0-9_\\\"`]+))?"
                    + "\\s+on\\s+([a-zA-Z0-9_\\.\\\"`]+)\\s*(?:using\\s+[a-zA-Z0-9_]+\\s*)?\\(([^)]+)\\)\\s*"
    );
    private static final Pattern COMMENT_ON_STATEMENT = Pattern.compile("(?is)^comment\\s+on\\s+.*");

    /** Optional referential-action tail of a FOREIGN KEY clause; carries no schema metadata. */
    private static final String FK_ACTIONS =
            "(?:\\s+on\\s+(?:delete|update)\\s+(?:cascade|restrict|set\\s+null|set\\s+default|no\\s+action))*";

    private static final Pattern NAMED_PRIMARY_KEY = Pattern.compile(
            "(?is)constraint\\s+([a-zA-Z0-9_\\\"`]+)\\s+primary\\s+key\\s*\\(([^)]+)\\)");
    private static final Pattern NAMED_UNIQUE = Pattern.compile(
            "(?is)constraint\\s+([a-zA-Z0-9_\\\"`]+)\\s+unique\\s*\\(([^)]+)\\)");
    private static final Pattern NAMED_FOREIGN_KEY = Pattern.compile(
            "(?is)constraint\\s+([a-zA-Z0-9_\\\"`]+)\\s+foreign\\s+key\\s*\\(([^)]+)\\)\\s+references\\s+"
                    + "([a-zA-Z0-9_\\\"`\\.]+)\\s*\\(([^)]+)\\)" + FK_ACTIONS);
    private static final Pattern NAMED_CHECK = Pattern.compile(
            "(?is)constraint\\s+([a-zA-Z0-9_\\\"`]+)\\s+check\\s*\\(.*\\)");
    private static final Pattern INLINE_PRIMARY_KEY = Pattern.compile(
            "(?is)primary\\s+key\\s*\\(([^)]+)\\)");
    private static final Pattern INLINE_FOREIGN_KEY = Pattern.compile(
            "(?is)foreign\\s+key\\s*\\(([^)]+)\\)\\s+references\\s+([a-zA-Z0-9_\\\"`\\.]+)\\s*\\(([^)]+)\\)" + FK_ACTIONS);
    private static final Pattern NAMED_UNIQUE_KEY = Pattern.compile(
            "(?is)unique\\s+(?:key|index)\\s+([a-zA-Z0-9_\\\"`]+)\\s*\\(([^)]+)\\)");
    private static final Pattern UNNAMED_UNIQUE_KEY = Pattern.compile(
            "(?is)unique\\s+(?:key|index)\\s*\\(([^)]+)\\)");
    private static final Pattern INLINE_UNIQUE = Pattern.compile(
            "(?is)unique\\s*\\(([^)]+)\\)");
    private static final Pattern BODY_KEY_OR_INDEX = Pattern.compile(
            "(?is)(unique\\s+)?(?:key|index)\\s+([a-zA-Z0-9_\\\"`]+)\\s*\\(([^)]+)\\)");
    private static final Pattern COLUMN_INLINE_REFERENCES = Pattern.compile(
            "(?is).*\\sreferences\\s+([a-zA-Z0-9_\\\"`\\.]+)\\s*\\(([^)]+)\\).*");

    private static final Pattern BARE_IDENTIFIER = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_$]*");
    private static final Pattern TYPE_HEAD = Pattern.compile(
            "(?s)^([a-zA-Z_][a-zA-Z0-9_]*)\\s*(?:\\(([^)]*)\\))?(.*)$");

    public SchemaModel parse(String ddl, Dialect dialect, String defaultSchema) {
        var tables = new ArrayList<SchemaModel.TableMeta>();
        var views = new ArrayList<SchemaModel.ViewMeta>();
        var enumTypes = new ArrayList<SchemaModel.EnumTypeMeta>();
        var standaloneIndexes = new ArrayList<StandaloneIndex>();
        int unnamedStandaloneIndex = 1;

        for (String statement : SqlStatementSplitter.split(ddl)) {
            Matcher tableHead = CREATE_TABLE_HEAD.matcher(statement);
            if (tableHead.lookingAt()) {
                tables.add(parseCreateTable(statement, tableHead, dialect, defaultSchema, enumTypes));
                continue;
            }

            Matcher view = CREATE_VIEW_STATEMENT.matcher(statement);
            if (view.matches()) {
                QualifiedName viewName = parseQualifiedName(view.group(1), defaultSchema);
                views.add(new SchemaModel.ViewMeta(viewName.schema(), viewName.name(), parseViewColumns(view.group(2))));
                continue;
            }

            Matcher index = CREATE_INDEX_STATEMENT.matcher(statement);
            if (index.matches()) {
                standaloneIndexes.add(parseStandaloneIndex(statement, index, defaultSchema, unnamedStandaloneIndex++));
                continue;
            }

            if (COMMENT_ON_STATEMENT.matcher(statement).matches()) {
                continue; // No schema metadata; the JDBC introspector does not surface comments either.
            }

            throw DdlParseException.unrecognizedStatement(statement);
        }

        applyStandaloneIndexes(standaloneIndexes, tables);

        tables.sort(Comparator.comparing(SchemaModel.TableMeta::schema).thenComparing(SchemaModel.TableMeta::name));
        views.sort(Comparator.comparing(SchemaModel.ViewMeta::schema).thenComparing(SchemaModel.ViewMeta::name));
        enumTypes.sort(Comparator
                .comparing((SchemaModel.EnumTypeMeta e) -> e.schema() == null ? "" : e.schema())
                .thenComparing(SchemaModel.EnumTypeMeta::name));
        return new SchemaModel(List.copyOf(tables), List.copyOf(views), List.copyOf(enumTypes));
    }

    // ------------------------------------------------------------------
    // CREATE TABLE
    // ------------------------------------------------------------------

    private SchemaModel.TableMeta parseCreateTable(
            String statement,
            Matcher tableHead,
            Dialect dialect,
            String defaultSchema,
            List<SchemaModel.EnumTypeMeta> enumTypes
    ) {
        QualifiedName tableName = parseQualifiedName(tableHead.group(1), defaultSchema);
        int bodyStart = tableHead.end(); // just past the opening '('
        int bodyEnd = findBalancedClose(statement, bodyStart - 1);
        if (bodyEnd < 0) {
            throw DdlParseException.unrecognizedStatement(statement);
        }
        String trailing = statement.substring(bodyEnd + 1).trim();
        if (!trailing.isEmpty()) {
            // ENGINE=InnoDB, WITH (...), PARTITION BY ... — valid SQL this parser cannot
            // represent faithfully; never drop it silently.
            throw DdlParseException.unrecognizedStatement(statement);
        }
        String tableBody = statement.substring(bodyStart, bodyEnd);

        var columns = new ArrayList<SchemaModel.ColumnMeta>();
        var constraints = new ArrayList<SchemaModel.ConstraintMeta>();
        var foreignKeys = new ArrayList<SchemaModel.ForeignKeyMeta>();
        var indexes = new ArrayList<SchemaModel.IndexMeta>();
        int unnamedPk = 1;
        int unnamedUq = 1;
        int unnamedFk = 1;
        int unnamedIdx = 1;

        for (String rawPart : splitCommaAware(tableBody)) {
            String part = SqlStatementSplitter.stripLeadingComments(rawPart).trim();
            if (part.isEmpty()) {
                continue;
            }
            String lower = part.toLowerCase(Locale.ROOT);

            if (lower.startsWith("constraint")) {
                Matcher m;
                if ((m = NAMED_PRIMARY_KEY.matcher(part)).matches()) {
                    constraints.add(new SchemaModel.ConstraintMeta(
                            unquoteIdentifier(m.group(1)), SchemaModel.ConstraintType.PRIMARY_KEY,
                            parseColumnList(m.group(2))));
                } else if ((m = NAMED_UNIQUE.matcher(part)).matches()) {
                    constraints.add(new SchemaModel.ConstraintMeta(
                            unquoteIdentifier(m.group(1)), SchemaModel.ConstraintType.UNIQUE,
                            parseColumnList(m.group(2))));
                } else if ((m = NAMED_FOREIGN_KEY.matcher(part)).matches()) {
                    QualifiedName referenced = parseQualifiedName(m.group(3), tableName.schema());
                    foreignKeys.add(new SchemaModel.ForeignKeyMeta(
                            unquoteIdentifier(m.group(1)), parseColumnList(m.group(2)),
                            referenced.schema(), referenced.name(), parseColumnList(m.group(4))));
                } else if (NAMED_CHECK.matcher(part).matches()) {
                    // CHECK constraints are not representable in SchemaModel; the JDBC
                    // introspector does not read them either, so ignoring preserves equivalence.
                } else {
                    throw DdlParseException.unrecognizedTablePart(tableName.name(), part);
                }
                continue;
            }

            if (lower.startsWith("primary key")) {
                Matcher m = INLINE_PRIMARY_KEY.matcher(part);
                if (!m.matches()) {
                    throw DdlParseException.unrecognizedTablePart(tableName.name(), part);
                }
                constraints.add(new SchemaModel.ConstraintMeta(
                        "pk_" + unnamedPk++, SchemaModel.ConstraintType.PRIMARY_KEY, parseColumnList(m.group(1))));
                continue;
            }

            if (lower.startsWith("unique")) {
                Matcher m;
                if ((m = NAMED_UNIQUE_KEY.matcher(part)).matches()) {
                    constraints.add(new SchemaModel.ConstraintMeta(
                            unquoteIdentifier(m.group(1)), SchemaModel.ConstraintType.UNIQUE,
                            parseColumnList(m.group(2))));
                } else if ((m = UNNAMED_UNIQUE_KEY.matcher(part)).matches()
                        || (m = INLINE_UNIQUE.matcher(part)).matches()) {
                    constraints.add(new SchemaModel.ConstraintMeta(
                            "uq_" + unnamedUq++, SchemaModel.ConstraintType.UNIQUE, parseColumnList(m.group(1))));
                } else {
                    throw DdlParseException.unrecognizedTablePart(tableName.name(), part);
                }
                continue;
            }

            if (lower.startsWith("foreign key")) {
                Matcher m = INLINE_FOREIGN_KEY.matcher(part);
                if (!m.matches()) {
                    throw DdlParseException.unrecognizedTablePart(tableName.name(), part);
                }
                QualifiedName referenced = parseQualifiedName(m.group(2), tableName.schema());
                foreignKeys.add(new SchemaModel.ForeignKeyMeta(
                        "fk_" + unnamedFk++, parseColumnList(m.group(1)),
                        referenced.schema(), referenced.name(), parseColumnList(m.group(3))));
                continue;
            }

            if (lower.startsWith("check")) {
                continue; // See CHECK note above.
            }

            if (lower.startsWith("exclude") || lower.startsWith("like ")
                    || lower.startsWith("period ") || lower.startsWith("fulltext") || lower.startsWith("spatial")) {
                // Table clauses this parser cannot represent; never treat them as columns.
                throw DdlParseException.unrecognizedTablePart(tableName.name(), part);
            }

            if (lower.startsWith("key ") || lower.startsWith("key(")
                    || lower.startsWith("index ") || lower.startsWith("index(")) {
                Matcher m = BODY_KEY_OR_INDEX.matcher(part);
                if (!m.matches()) {
                    throw DdlParseException.unrecognizedTablePart(tableName.name(), part);
                }
                indexes.add(new SchemaModel.IndexMeta(
                        unquoteIdentifier(m.group(2)), m.group(1) != null, parseColumnList(m.group(3))));
                continue;
            }

            // Anything else must be a column definition.
            ColumnParse column = parseColumnDefinition(tableName.name(), part, dialect);
            columns.add(column.column());
            if (column.inlinePrimaryKey()) {
                constraints.add(new SchemaModel.ConstraintMeta(
                        "pk_" + unnamedPk++, SchemaModel.ConstraintType.PRIMARY_KEY,
                        List.of(column.column().name())));
            }
            if (column.inlineUnique()) {
                constraints.add(new SchemaModel.ConstraintMeta(
                        "uq_" + unnamedUq++, SchemaModel.ConstraintType.UNIQUE,
                        List.of(column.column().name())));
            }
            if (column.inlineReference() != null) {
                foreignKeys.add(new SchemaModel.ForeignKeyMeta(
                        "fk_" + unnamedFk++, List.of(column.column().name()),
                        column.inlineReference().schemaOrDefault(tableName.schema()),
                        column.inlineReference().table(),
                        column.inlineReference().columns()));
            }
            if (column.column().enumTypeName() != null) {
                enumTypes.add(new SchemaModel.EnumTypeMeta(
                        tableName.schema(), column.column().enumTypeName(), column.column().enumValues()));
            }
        }

        var mergedIndexes = mergeIndexes(indexes, deriveIndexesFromConstraints(constraints, unnamedIdx));
        if (dialect == Dialect.MYSQL) {
            mergedIndexes = mergeIndexes(mergedIndexes, deriveIndexesFromForeignKeys(foreignKeys, mergedIndexes));
            // MySQL ignores explicit PRIMARY KEY constraint names; information_schema (and the
            // JDBC introspector) always reports "PRIMARY".
            for (int i = 0; i < constraints.size(); i++) {
                if (constraints.get(i).type() == SchemaModel.ConstraintType.PRIMARY_KEY) {
                    constraints.set(i, new SchemaModel.ConstraintMeta(
                            "PRIMARY", SchemaModel.ConstraintType.PRIMARY_KEY, constraints.get(i).columns()));
                }
            }
        }

        var finalColumns = applyPrimaryKeyNotNull(columns, constraints);
        constraints.sort(Comparator.comparing(SchemaModel.ConstraintMeta::name));
        foreignKeys.sort(Comparator.comparing(SchemaModel.ForeignKeyMeta::name));
        var sortedIndexes = new ArrayList<>(mergedIndexes);
        sortedIndexes.sort(Comparator.comparing(SchemaModel.IndexMeta::name));

        return new SchemaModel.TableMeta(
                tableName.schema(), tableName.name(), finalColumns,
                List.copyOf(constraints), List.copyOf(foreignKeys), List.copyOf(sortedIndexes));
    }

    // ------------------------------------------------------------------
    // Column definitions
    // ------------------------------------------------------------------

    private record InlineReference(String schema, String table, List<String> columns) {
        String schemaOrDefault(String defaultSchema) {
            return schema == null ? defaultSchema : schema;
        }
    }

    private record ColumnParse(
            SchemaModel.ColumnMeta column,
            boolean inlinePrimaryKey,
            boolean inlineUnique,
            InlineReference inlineReference
    ) {}

    private ColumnParse parseColumnDefinition(String tableName, String part, Dialect dialect) {
        String[] nameAndRest = splitColumnName(part);
        if (nameAndRest == null) {
            throw DdlParseException.unrecognizedTablePart(tableName, part);
        }
        String name = unquoteIdentifier(nameAndRest[0]);
        String rest = nameAndRest[1].trim();

        TypeInfo type = scanType(rest, dialect, tableName, name);
        if (type == null) {
            throw DdlParseException.unrecognizedTablePart(tableName, part);
        }

        String lowerPart = part.toLowerCase(Locale.ROOT);
        String lowerRemainder = type.remainder().toLowerCase(Locale.ROOT);
        boolean nullable = !lowerPart.contains("not null");
        boolean autoIncrement = isAutoIncrementColumn(dialect, type.rawHead(), lowerPart);

        String sqlType = type.enumValues() != null
                ? "enum"
                : SchemaIntrospector.normalizeType(dialect, type.canonicalName());
        String enumTypeName = type.enumValues() != null ? SchemaIntrospector.mysqlEnumTypeName(tableName, name) : null;
        List<String> enumValues = type.enumValues() != null ? type.enumValues() : List.of();

        var column = new SchemaModel.ColumnMeta(
                name,
                sqlType,
                nullable,
                type.precision(),
                type.scale(),
                type.length(),
                type.canonicalName(),
                enumTypeName,
                enumValues,
                autoIncrement
        );

        boolean inlinePk = lowerRemainder.contains("primary key");
        boolean inlineUnique = !inlinePk && containsWord(lowerRemainder, "unique");
        InlineReference reference = null;
        Matcher references = COLUMN_INLINE_REFERENCES.matcher(part);
        if (references.matches()) {
            QualifiedName referenced = parseQualifiedName(references.group(1), null);
            reference = new InlineReference(referenced.schema(), referenced.name(), parseColumnList(references.group(2)));
        }
        return new ColumnParse(column, inlinePk, inlineUnique, reference);
    }

    private static String[] splitColumnName(String part) {
        String trimmed = part.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        char first = trimmed.charAt(0);
        if (first == '"' || first == '`') {
            int close = trimmed.indexOf(first, 1);
            if (close < 0 || close + 1 >= trimmed.length()) {
                return null;
            }
            return new String[] {trimmed.substring(0, close + 1), trimmed.substring(close + 1)};
        }
        int space = indexOfWhitespace(trimmed);
        if (space < 0) {
            return null;
        }
        String name = trimmed.substring(0, space);
        if (!BARE_IDENTIFIER.matcher(name).matches()) {
            return null;
        }
        return new String[] {name, trimmed.substring(space)};
    }

    private static int indexOfWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static boolean containsWord(String haystack, String word) {
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(haystack).find();
    }

    /**
     * Scanned column type: the canonical {@code information_schema.columns.data_type}-style
     * name, plus length/precision/scale mirroring what JDBC introspection reports.
     */
    private record TypeInfo(
            String canonicalName,
            String rawHead,
            Integer length,
            Integer precision,
            Integer scale,
            List<String> enumValues,
            String remainder
    ) {}

    private TypeInfo scanType(String rest, Dialect dialect, String tableName, String columnName) {
        Matcher head = TYPE_HEAD.matcher(rest);
        if (!head.matches()) {
            return null;
        }
        String word = head.group(1).toLowerCase(Locale.ROOT);
        String args = head.group(2);
        String remainder = head.group(3) == null ? "" : head.group(3);

        // MySQL inline enum types keep their value list in the parentheses. Length mirrors
        // information_schema.columns.character_maximum_length: the longest enum literal.
        if (dialect == Dialect.MYSQL && "enum".equals(word) && args != null) {
            List<String> values = SchemaIntrospector.parseMysqlEnumValues("enum(" + args + ")");
            Integer length = values.stream().map(String::length).max(Integer::compareTo).orElse(null);
            return new TypeInfo("enum", word, length, null, null, values, remainder);
        }

        // Multi-word type names; arguments may follow either word (e.g. "timestamp(3) with time zone").
        MultiWord multiWord = consumeMultiWord(word, remainder);
        if (multiWord != null) {
            word = multiWord.name();
            remainder = multiWord.remainder();
            if (args == null && multiWord.args() != null) {
                args = multiWord.args();
            }
        }

        boolean unsigned = false;
        if (dialect == Dialect.MYSQL) {
            Matcher modifier = Pattern.compile("(?is)^\\s*(unsigned|signed|zerofill)\\b(.*)$").matcher(remainder);
            while (modifier.matches()) {
                unsigned |= "unsigned".equalsIgnoreCase(modifier.group(1));
                remainder = modifier.group(2);
                modifier = Pattern.compile("(?is)^\\s*(unsigned|signed|zerofill)\\b(.*)$").matcher(remainder);
            }
        }

        String canonical = canonicalTypeName(dialect, word);
        Integer argPrecision = null;
        Integer argScale = null;
        Integer argLength = null;
        if (args != null && !args.isBlank()) {
            String[] argParts = args.split(",");
            try {
                int firstArg = Integer.parseInt(argParts[0].trim());
                if (isCharacterType(dialect, canonical)) {
                    argLength = firstArg;
                } else {
                    argPrecision = firstArg;
                    argScale = argParts.length > 1 ? Integer.parseInt(argParts[1].trim()) : 0;
                }
            } catch (NumberFormatException e) {
                return null; // Non-numeric type arguments (e.g. expressions) are not parseable.
            }
        }

        Defaults defaults = typeDefaults(dialect, canonical, unsigned);
        Integer length = argLength != null ? argLength : defaults.length();
        Integer precision = isNumericArgsType(canonical) && argPrecision != null ? argPrecision : defaults.precision();
        Integer scale = isNumericArgsType(canonical) && argScale != null ? argScale : defaults.scale();
        return new TypeInfo(canonical, word, length, precision, scale, null, remainder);
    }

    private record MultiWord(String name, String args, String remainder) {}

    private static MultiWord consumeMultiWord(String firstWord, String remainder) {
        return switch (firstWord) {
            case "character", "char" -> consumeWords(remainder, "character varying", "varying");
            case "bit" -> consumeWords(remainder, "bit varying", "varying");
            case "double" -> consumeWords(remainder, "double precision", "precision");
            case "timestamp" -> consumeTimeZoneSuffix("timestamp", remainder);
            case "time" -> consumeTimeZoneSuffix("time", remainder);
            default -> null;
        };
    }

    private static MultiWord consumeWords(String remainder, String fullName, String nextWord) {
        Matcher m = Pattern.compile("(?is)^\\s*" + nextWord + "\\b\\s*(?:\\(([^)]*)\\))?(.*)$").matcher(remainder);
        if (m.matches()) {
            return new MultiWord(fullName, m.group(1), m.group(2));
        }
        return null;
    }

    private static MultiWord consumeTimeZoneSuffix(String base, String remainder) {
        Matcher m = Pattern.compile("(?is)^\\s*(with|without)\\s+time\\s+zone\\b(.*)$").matcher(remainder);
        if (m.matches()) {
            return new MultiWord(base + " " + m.group(1).toLowerCase(Locale.ROOT) + " time zone", null, m.group(2));
        }
        return null;
    }

    private static boolean isCharacterType(Dialect dialect, String canonical) {
        return switch (canonical) {
            case "character varying", "character", "varchar", "char", "bit varying",
                 "binary", "varbinary", "nchar", "nvarchar" -> true;
            default -> false;
        };
    }

    private static boolean isNumericArgsType(String canonical) {
        return switch (canonical) {
            case "numeric", "decimal", "float", "double", "real", "double precision",
                 "tinyint", "smallint", "mediumint", "int", "integer", "bigint", "bit" -> true;
            default -> false;
        };
    }

    private record Defaults(Integer length, Integer precision, Integer scale) {}

    /**
     * Dialect defaults mirroring {@code information_schema.columns} for common types, so the
     * fallback parser fills the same precision/scale/length JDBC introspection reports.
     */
    private static Defaults typeDefaults(Dialect dialect, String canonical, boolean unsigned) {
        return switch (dialect) {
            case POSTGRESQL -> switch (canonical) {
                case "smallint" -> new Defaults(null, 16, 0);
                case "integer" -> new Defaults(null, 32, 0);
                case "bigint" -> new Defaults(null, 64, 0);
                case "real" -> new Defaults(null, 24, null);
                case "double precision" -> new Defaults(null, 53, null);
                case "character" -> new Defaults(1, null, null);
                default -> new Defaults(null, null, null);
            };
            case MYSQL -> switch (canonical) {
                case "tinyint" -> new Defaults(null, 3, 0);
                case "smallint" -> new Defaults(null, 5, 0);
                case "mediumint" -> new Defaults(null, 7, 0);
                case "int" -> new Defaults(null, 10, 0);
                case "bigint" -> new Defaults(null, unsigned ? 20 : 19, 0);
                case "decimal" -> new Defaults(null, 10, 0);
                case "float" -> new Defaults(null, 12, null);
                case "double" -> new Defaults(null, 22, null);
                case "char" -> new Defaults(1, null, null);
                case "tinytext", "tinyblob" -> new Defaults(255, null, null);
                case "text", "blob" -> new Defaults(65535, null, null);
                case "mediumtext", "mediumblob" -> new Defaults(16777215, null, null);
                default -> new Defaults(null, null, null);
            };
        };
    }

    /** Canonicalizes type aliases to the {@code information_schema.columns.data_type} spelling. */
    private static String canonicalTypeName(Dialect dialect, String name) {
        return switch (dialect) {
            case POSTGRESQL -> switch (name) {
                case "int", "int4", "integer", "serial", "serial4" -> "integer";
                case "int8", "bigint", "bigserial", "serial8" -> "bigint";
                case "int2", "smallint", "smallserial", "serial2" -> "smallint";
                case "varchar", "character varying" -> "character varying";
                case "char", "character" -> "character";
                case "bool", "boolean" -> "boolean";
                case "decimal", "numeric" -> "numeric";
                case "float4", "real" -> "real";
                case "float8", "float", "double precision" -> "double precision";
                case "timestamptz", "timestamp with time zone" -> "timestamp with time zone";
                case "timestamp", "timestamp without time zone" -> "timestamp without time zone";
                case "timetz", "time with time zone" -> "time with time zone";
                case "time", "time without time zone" -> "time without time zone";
                default -> name;
            };
            case MYSQL -> switch (name) {
                case "integer", "int" -> "int";
                case "dec", "fixed", "numeric", "decimal" -> "decimal";
                case "bool", "boolean" -> "tinyint";
                case "character varying", "varchar" -> "varchar";
                case "character", "char" -> "char";
                case "double precision", "double" -> "double";
                default -> name;
            };
        };
    }

    private static boolean isAutoIncrementColumn(Dialect dialect, String rawTypeHead, String lowerPart) {
        return switch (dialect) {
            case POSTGRESQL -> rawTypeHead.startsWith("serial")
                    || rawTypeHead.startsWith("bigserial")
                    || rawTypeHead.startsWith("smallserial")
                    || (lowerPart.contains(" generated") && lowerPart.contains(" identity"));
            case MYSQL -> lowerPart.contains("auto_increment");
        };
    }

    // ------------------------------------------------------------------
    // Indexes
    // ------------------------------------------------------------------

    private record StandaloneIndex(QualifiedName table, SchemaModel.IndexMeta index) {}

    private StandaloneIndex parseStandaloneIndex(
            String statement,
            Matcher matcher,
            String defaultSchema,
            int unnamedCounter
    ) {
        boolean unique = matcher.group(1) != null;
        QualifiedName tableName = parseQualifiedName(matcher.group(3), defaultSchema);
        List<String> columns = parseColumnList(matcher.group(4));
        for (String column : columns) {
            if (column.indexOf('(') >= 0 || column.indexOf(')') >= 0) {
                // Expression indexes are not representable; JDBC introspection reports them
                // differently, so refusing is safer than diverging.
                throw DdlParseException.unrecognizedStatement(statement);
            }
        }
        String indexName = matcher.group(2) == null || matcher.group(2).isBlank()
                ? "idx_" + tableName.name() + "_" + String.join("_", columns) + "_" + unnamedCounter
                : unquoteIdentifier(matcher.group(2));
        return new StandaloneIndex(tableName, new SchemaModel.IndexMeta(indexName, unique, columns));
    }

    private void applyStandaloneIndexes(List<StandaloneIndex> standaloneIndexes, List<SchemaModel.TableMeta> tables) {
        for (StandaloneIndex standalone : standaloneIndexes) {
            boolean applied = false;
            for (int i = 0; i < tables.size(); i++) {
                SchemaModel.TableMeta table = tables.get(i);
                if (!table.schema().equals(standalone.table().schema())
                        || !table.name().equals(standalone.table().name())) {
                    continue;
                }
                var mergedIndexes = new ArrayList<>(mergeIndexes(table.indexes(), List.of(standalone.index())));
                mergedIndexes.sort(Comparator.comparing(SchemaModel.IndexMeta::name));
                tables.set(i, new SchemaModel.TableMeta(
                        table.schema(), table.name(), table.columns(),
                        table.constraints(), table.foreignKeys(), List.copyOf(mergedIndexes)));
                applied = true;
                break;
            }
            if (!applied) {
                throw new DdlParseException(
                        "CREATE INDEX " + standalone.index().name() + " targets table "
                                + standalone.table().schema() + "." + standalone.table().name()
                                + ", which is not defined in the parsed DDL. The fallback parser refuses to drop "
                                + "the index silently; define the table in the same DDL set or use container-backed "
                                + "introspection (titan { database.ddlMode = 'container' }).");
            }
        }
    }

    private static List<SchemaModel.IndexMeta> deriveIndexesFromConstraints(
            List<SchemaModel.ConstraintMeta> constraints,
            int unnamedCounterStart
    ) {
        var indexes = new ArrayList<SchemaModel.IndexMeta>();
        int unnamedCounter = unnamedCounterStart;
        for (SchemaModel.ConstraintMeta constraint : constraints) {
            if (constraint.type() == SchemaModel.ConstraintType.UNIQUE) {
                String name = (constraint.name() == null || constraint.name().isBlank())
                        ? "idx_uq_constraint_" + unnamedCounter++
                        : constraint.name();
                indexes.add(new SchemaModel.IndexMeta(name, true, constraint.columns()));
            }
        }
        return indexes;
    }

    private static List<SchemaModel.IndexMeta> deriveIndexesFromForeignKeys(
            List<SchemaModel.ForeignKeyMeta> foreignKeys,
            List<SchemaModel.IndexMeta> existingIndexes
    ) {
        var indexes = new ArrayList<SchemaModel.IndexMeta>();
        for (SchemaModel.ForeignKeyMeta foreignKey : foreignKeys) {
            List<String> foreignKeyColumns = foreignKey.columns();
            if (foreignKeyColumns.isEmpty()) {
                continue;
            }

            boolean coveredByExistingIndex = existingIndexes.stream()
                    .map(SchemaModel.IndexMeta::columns)
                    .anyMatch(indexColumns -> startsWithColumns(indexColumns, foreignKeyColumns));
            if (coveredByExistingIndex) {
                continue;
            }

            // MySQL names the implicitly created index after the FK constraint, which is also
            // what JDBC introspection reports.
            indexes.add(new SchemaModel.IndexMeta(foreignKey.name(), false, foreignKeyColumns));
        }
        return indexes;
    }

    private static boolean startsWithColumns(List<String> indexColumns, List<String> prefixColumns) {
        if (indexColumns.size() < prefixColumns.size()) {
            return false;
        }
        for (int i = 0; i < prefixColumns.size(); i++) {
            if (!indexColumns.get(i).equals(prefixColumns.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static List<SchemaModel.IndexMeta> mergeIndexes(List<SchemaModel.IndexMeta> first, List<SchemaModel.IndexMeta> second) {
        var merged = new ArrayList<SchemaModel.IndexMeta>();
        merged.addAll(first);
        for (SchemaModel.IndexMeta index : second) {
            boolean exists = merged.stream().anyMatch(existing -> existing.unique() == index.unique()
                    && existing.columns().equals(index.columns()));
            if (!exists) {
                merged.add(index);
            }
        }
        return merged;
    }

    // ------------------------------------------------------------------
    // Views
    // ------------------------------------------------------------------

    private List<SchemaModel.ColumnMeta> parseViewColumns(String selectProjection) {
        var columns = new ArrayList<SchemaModel.ColumnMeta>();
        for (String rawExpr : splitCommaAware(selectProjection)) {
            String expr = rawExpr.trim();
            String alias = inferAlias(expr);
            columns.add(new SchemaModel.ColumnMeta(
                    alias,
                    "unknown",
                    true,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of()
            ));
        }
        return columns;
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    private static List<SchemaModel.ColumnMeta> applyPrimaryKeyNotNull(
            List<SchemaModel.ColumnMeta> columns,
            List<SchemaModel.ConstraintMeta> constraints
    ) {
        var primaryKeyColumns = constraints.stream()
                .filter(c -> c.type() == SchemaModel.ConstraintType.PRIMARY_KEY)
                .flatMap(c -> c.columns().stream())
                .collect(java.util.stream.Collectors.toSet());

        if (primaryKeyColumns.isEmpty()) {
            return columns;
        }

        return columns.stream().map(column -> {
            if (!primaryKeyColumns.contains(column.name()) || !column.nullable()) {
                return column;
            }
            return new SchemaModel.ColumnMeta(
                    column.name(),
                    column.sqlType(),
                    false,
                    column.precision(),
                    column.scale(),
                    column.length(),
                    column.dbTypeName(),
                    column.enumTypeName(),
                    column.enumValues(),
                    column.autoIncrement()
            );
        }).toList();
    }

    /**
     * Index just past the opening paren at {@code openIndex} to its balanced closing paren,
     * quote-aware. Returns -1 when unbalanced.
     */
    private static int findBalancedClose(String text, int openIndex) {
        int depth = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inBacktick = false;
        for (int i = openIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inSingleQuote) {
                if (c == '\\') {
                    i++;
                } else if (c == '\'') {
                    inSingleQuote = false;
                }
                continue;
            }
            if (inDoubleQuote) {
                if (c == '"') {
                    inDoubleQuote = false;
                }
                continue;
            }
            if (inBacktick) {
                if (c == '`') {
                    inBacktick = false;
                }
                continue;
            }
            switch (c) {
                case '\'' -> inSingleQuote = true;
                case '"' -> inDoubleQuote = true;
                case '`' -> inBacktick = true;
                case '(' -> depth++;
                case ')' -> {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                }
                default -> { }
            }
        }
        return -1;
    }

    private static List<String> parseColumnList(String csv) {
        return splitCommaAware(csv).stream()
                .map(String::trim)
                .map(DdlSchemaParser::unquoteIdentifier)
                .map(value -> value.replaceAll("(?i)\\s+(asc|desc)$", "").trim())
                .toList();
    }

    private static String inferAlias(String expr) {
        Matcher asMatcher = Pattern.compile("(?is).+\\s+as\\s+([a-zA-Z0-9_\\\"]+)$").matcher(expr);
        if (asMatcher.matches()) {
            return unquoteIdentifier(asMatcher.group(1));
        }

        String cleaned = expr.replaceAll("[\\\"`]", "").trim();
        int dot = cleaned.lastIndexOf('.');
        if (dot >= 0 && dot < cleaned.length() - 1) {
            return cleaned.substring(dot + 1).trim();
        }

        int space = cleaned.lastIndexOf(' ');
        if (space >= 0 && space < cleaned.length() - 1) {
            return cleaned.substring(space + 1).trim();
        }

        return cleaned;
    }

    private static QualifiedName parseQualifiedName(String raw, String defaultSchema) {
        String trimmed = raw.trim();
        int separator = findQualifierSeparator(trimmed);
        if (separator >= 0) {
            String schema = unquoteIdentifier(trimmed.substring(0, separator));
            String name = unquoteIdentifier(trimmed.substring(separator + 1));
            return new QualifiedName(schema, name);
        }
        return new QualifiedName(defaultSchema, unquoteIdentifier(trimmed));
    }

    private static int findQualifierSeparator(String value) {
        boolean inDoubleQuote = false;
        boolean inBacktick = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' && !inBacktick) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }
            if (c == '`' && !inDoubleQuote) {
                inBacktick = !inBacktick;
                continue;
            }
            if (c == '.' && !inDoubleQuote && !inBacktick) {
                return i;
            }
        }
        return -1;
    }

    private static String unquoteIdentifier(String value) {
        String trimmed = value.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("`") && trimmed.endsWith("`"))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static List<String> splitCommaAware(String input) {
        var parts = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        int parenDepth = 0;
        boolean inSingleQuote = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\'' && (i == 0 || input.charAt(i - 1) != '\\')) {
                inSingleQuote = !inSingleQuote;
            }
            if (!inSingleQuote) {
                if (c == '(') {
                    parenDepth++;
                } else if (c == ')') {
                    parenDepth = Math.max(0, parenDepth - 1);
                } else if (c == ',' && parenDepth == 0) {
                    parts.add(current.toString());
                    current.setLength(0);
                    continue;
                }
            }
            current.append(c);
        }

        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }

    private record QualifiedName(String schema, String name) {}
}
