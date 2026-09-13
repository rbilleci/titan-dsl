package io.titan.introspect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class SchemaJsonWriterTest {

    @Test
    void writesDeterministicSchemaJson() {
        SchemaModel model = new SchemaModel(
                List.of(
                        new SchemaModel.TableMeta("public", "accounts", List.of(
                                new SchemaModel.ColumnMeta("email", "varchar", false, null, null, 255),
                                new SchemaModel.ColumnMeta("id", "integer", false, 32, 0, null, null, null, List.of(), true),
                                new SchemaModel.ColumnMeta("status", "enum", false, null, null, null, "USER-DEFINED", "account_status", List.of("ACTIVE", "SUSPENDED"))
                        ), List.of(
                                new SchemaModel.ConstraintMeta("accounts_pkey", SchemaModel.ConstraintType.PRIMARY_KEY, List.of("id"))
                        ), List.of(), List.of(
                                new SchemaModel.IndexMeta("idx_accounts_email", true, List.of("email"))
                        ))
                ),
                List.of(
                        new SchemaModel.ViewMeta("public", "active_accounts", List.of(
                                new SchemaModel.ColumnMeta("email", "varchar", false, null, null, 255),
                                new SchemaModel.ColumnMeta("id", "integer", false, 32, 0, null)
                        ))
                ),
                List.of(
                        new SchemaModel.EnumTypeMeta("public", "account_status", List.of("ACTIVE", "SUSPENDED"))
                )
        );

        String json = new SchemaJsonWriter().write(model);

        assertEquals("""
                {
                  "tables": [
                    {
                      "schema": "public",
                      "name": "accounts",
                      "columns": [
                        {
                          "name": "email",
                          "sqlType": "varchar",
                          "nullable": false,
                          "precision": null,
                          "scale": null,
                          "length": 255,
                          "autoIncrement": false,
                          "enumTypeName": null,
                          "enumValues": []
                        },
                        {
                          "name": "id",
                          "sqlType": "integer",
                          "nullable": false,
                          "precision": 32,
                          "scale": 0,
                          "length": null,
                          "autoIncrement": true,
                          "enumTypeName": null,
                          "enumValues": []
                        },
                        {
                          "name": "status",
                          "sqlType": "enum",
                          "nullable": false,
                          "precision": null,
                          "scale": null,
                          "length": null,
                          "autoIncrement": false,
                          "enumTypeName": "account_status",
                          "enumValues": ["ACTIVE", "SUSPENDED"]
                        }
                      ],
                      "constraints": [
                        {
                          "name": "accounts_pkey",
                          "type": "PRIMARY_KEY",
                          "columns": ["id"]
                        }
                      ],
                      "foreignKeys": [
                      ],
                      "indexes": [
                        {
                          "name": "idx_accounts_email",
                          "unique": true,
                          "columns": ["email"]
                        }
                      ]
                    }
                  ],
                  "views": [
                    {
                      "schema": "public",
                      "name": "active_accounts",
                      "columns": [
                        {
                          "name": "email",
                          "sqlType": "varchar",
                          "nullable": false,
                          "precision": null,
                          "scale": null,
                          "length": 255,
                          "autoIncrement": false,
                          "enumTypeName": null,
                          "enumValues": []
                        },
                        {
                          "name": "id",
                          "sqlType": "integer",
                          "nullable": false,
                          "precision": 32,
                          "scale": 0,
                          "length": null,
                          "autoIncrement": false,
                          "enumTypeName": null,
                          "enumValues": []
                        }
                      ]
                    }
                  ],
                  "enumTypes": [
                    {
                      "schema": "public",
                      "name": "account_status",
                      "values": ["ACTIVE", "SUSPENDED"]
                    }
                  ]
                }
                """, json);
    }
}
