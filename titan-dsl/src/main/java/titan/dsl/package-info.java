// SPDX-License-Identifier: GPL-3.0-only
/**
 * Typed SQL construction and rendering without a database connection or external runtime.
 *
 * <p>Use {@link titan.dsl.DSL} to construct queries and
 * {@link titan.dsl.SelectBuilder#render(titan.dsl.SqlDialect)} to obtain SQL with
 * ordered bind values. Identifiers and raw fragments must be trusted developer input.
 * Fetch-named methods describe query forms; this package does not execute JDBC calls.</p>
 *
 * <p>Titan DSL is licensed under GNU GPL version 3 only, without an additional
 * linking exception. See the LICENSE file included with the distribution.</p>
 */
package titan.dsl;
