/*
 * Copyright (C) 2009 Trustees of the University of Pennsylvania
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.upenn.cis.orchestra.sql.dtp;

import static edu.upenn.cis.orchestra.sql.dtp.SqlDtpUtil.getSQLQueryParserFactory;

import org.eclipse.datatools.modelbase.sql.query.QueryResultSpecification;
import org.eclipse.datatools.modelbase.sql.query.ResultColumn;
import org.eclipse.datatools.modelbase.sql.query.ResultTableAllColumns;
import org.eclipse.datatools.modelbase.sql.query.ValueExpressionColumn;
import org.eclipse.datatools.modelbase.sql.query.helper.StatementHelper;

import edu.upenn.cis.orchestra.sql.IColumnExpression;
import edu.upenn.cis.orchestra.sql.ISqlConstant;
import edu.upenn.cis.orchestra.sql.ISqlExp;
import edu.upenn.cis.orchestra.sql.ISqlSelectItem;

/**
 * DTP-backed {@code ISqlSelectItem}.
 * <p>
 * Intentionally package-private.
 * 
 * @author Sam Donnelly
 */
class DtpSqlSelectItem extends AbstractSQLQueryObject<QueryResultSpecification>
		implements ISqlSelectItem {

	/** Wrapped DTP object. */
	private final QueryResultSpecification _queryResultSpecification;

	/**
	 * Create a new {@code SELECT} item from a {@code QueryResultSpecification}.
	 * 
	 * @param queryResultSpecification from which we build the new {@code
	 *            DtpSqlSelectItem}.
	 */
	DtpSqlSelectItem(QueryResultSpecification queryResultSpecification) {
		_queryResultSpecification = queryResultSpecification;
	}

	/**
	 * Create a new {@code SELECT} item, given its name in {@code
	 * [[schema.]table.]column} format. May contain a {@code "*"} in the {@code
	 * column} position.
	 * <p>
	 * For backwards compatibility with the ZQL {@code SqlSelectItem}, we allow
	 * also {@code skolemstr(...)} udf's and {@code cast} expressions to be passed
	 * in through {@code fullname}. These expression should be built up using
	 * {@code edu.upenn.cis.orchestra.sql} classes, but that is not supported
	 * yet.
	 * <p>
	 * For expediency, we allow {@code count} functions here.
	 * 
	 * @param fullname a string that represents a column name or wildcard
	 *            (example: <code>SCHEMA.TABLE.*</code> or <code>TABLE.*</code>
	 *            or <code>*</code>), a {@code skolemstr(...)} udf, {@code
	 *            cast} expression, a {@code count}
	 */
	DtpSqlSelectItem(final String fullname) {
		final String fullnameTrimmed = fullname.trim();
		final String fullnameTrimmedLowerCase = fullnameTrimmed.toLowerCase();
		final String fullnameTrimmedLowerCaseNoSpaces = fullnameTrimmedLowerCase
				.replace(" ", "");

		if (fullnameTrimmedLowerCaseNoSpaces.startsWith("skolemstr(")
				|| fullnameTrimmedLowerCaseNoSpaces.startsWith("cast(")
				|| fullnameTrimmedLowerCaseNoSpaces.startsWith("count(")
				|| (fullnameTrimmed.startsWith("'") && fullnameTrimmed
						.endsWith("'")) || fullnameTrimmed.equals("1")) {
			_queryResultSpecification = getSQLQueryParserFactory()
					.createResultColumn(
							getSQLQueryParserFactory().createSimpleExpression(
									fullnameTrimmed), null);
			return;
		}

		final IColumnExpression column = _sqlFactory
				.newColumnExpression(fullname);
		if ("*".equals(column.getColumn())) {
			if (column.getTable() == null) {
				_queryResultSpecification = null;
				// DTP doesn't seem to have a way of representing a plain '*'
				// (with no preceding table) select item, instead its
				// represented by having no (or null)
				// select items in the select, so we're done and just return.
				return;
			}
			_queryResultSpecification = getSQLQueryParserFactory()
					.createResultTableAllColumns(column.getTableName(),
							column.getSchemaName());
		} else {
			_queryResultSpecification = getSQLQueryParserFactory()
					.createResultColumn(
							getSQLQueryParserFactory().createColumnExpression(
									column.getColumn(), column.getTableName(),
									column.getSchemaName()), null);

		}
	}

	/**
	 * Return {@code true} if this is a wildcard ({@code "[table.]*"}), {@code
	 * false} otherwise.
	 * 
	 * @return {@code true} if this is a wildcard, {@code false} otherwise.
	 */
	private boolean isWildcard() {
		if (_queryResultSpecification == null
				|| _queryResultSpecification instanceof ResultTableAllColumns) {
			return true;
		} else {
			return false;
		}
	}

	/** {@inheritDoc} */
	@Override
	public ISqlExp getExpression() {
		if (isWildcard()) {
			return null;
		} else {
			return _sqlFactory.newSqlConstant(getColumn(),
					ISqlConstant.Type.COLUMNNAME);
		}
	}

	/** {@inheritDoc} */
	@Override
	public String getAlias() {
		if (isWildcard()) {
			return null;
		}
		return getSQLQueryObject().getName();
	}

	/** {@inheritDoc} */
	@Override
	public ISqlSelectItem setAlias(final String alias) {
		if (isWildcard()) {
			throw new IllegalStateException(
					"Can't set alias for wildcarded column.");
		}
		_queryResultSpecification.setName(StatementHelper
				.convertSQLIdentifierToCatalogFormat(alias,
						_queryResultSpecification.getSourceInfo()
								.getSqlFormat().getDelimitedIdentifierQuote()));
		return this;
	}

	/** {@inheritDoc} */
	@Override
	public String getTable() {
		if (getSQLQueryObject() instanceof ResultColumn) {
			ResultColumn resultColumn = (ResultColumn) _queryResultSpecification;
			if (resultColumn.getValueExpr() instanceof ValueExpressionColumn) {
				ValueExpressionColumn valueExpressionColumn = (ValueExpressionColumn) resultColumn
						.getValueExpr();
				return valueExpressionColumn.getTableExpr() == null ? null
						: valueExpressionColumn.getTableExpr().getName();
			} else {
				return null;
			}
		} else if (getSQLQueryObject() instanceof ResultTableAllColumns) {
			return ((ResultTableAllColumns) getSQLQueryObject()).getTableExpr()
					.getName();
		} else {
			throw new IllegalStateException(
					"_queryResultSpecification is unexpected type: "
							+ _queryResultSpecification.getClass());
		}
	}

	/** {@inheritDoc} */
	@Override
	public String getColumn() {
		if (isWildcard()) {
			return "*";
		}
		final ResultColumn resultColumn = (ResultColumn) _queryResultSpecification;
		if (resultColumn.getValueExpr() instanceof ValueExpressionColumn) {
			final ValueExpressionColumn valueExpressionColumn = (ValueExpressionColumn) resultColumn
					.getValueExpr();
			return valueExpressionColumn.getName();
		} else {
			return resultColumn.getValueExpr().getSQL();
		}
	}

	/** {@inheritDoc} */
	@Override
	public QueryResultSpecification getSQLQueryObject() {
		return _queryResultSpecification;
	}
}

// else if (false) {
// if (fullnameTrimmed.equalsIgnoreCase("CAST(? AS INTEGER)")) {
// _queryResultSpecification = getSQLQueryParserFactory()
// .createResultColumn(
// getSQLQueryParserFactory()
// .createCastExpression(
// getSQLQueryParserFactory()
// .createSimpleExpression(
// "?"), "INTEGER"),
// null);
// return;
// } else if (fullnameTrimmed.equalsIgnoreCase("cast(0 as INT)")) {
// _queryResultSpecification = getSQLQueryParserFactory()
// .createResultColumn(
// getSQLQueryParserFactory()
// .createCastExpression(
// getSQLQueryParserFactory()
// .createSimpleExpression(
// "0"), "INTEGER"),
// null);
// return;
// } else if (fullnameTrimmed.toLowerCase().startsWith(
// "cast(null as varchar(")) {
// final String lengthStr = fullnameTrimmed.substring(
// "cast(null as varchar(".length(), fullnameTrimmed
// .length() - 2);
// _queryResultSpecification = getSQLQueryParserFactory()
// .createResultColumn(
// getSQLQueryParserFactory()
// .createCastExpression(
// getSQLQueryParserFactory()
// .createNullExpression(),
// getSQLQueryParserFactory()
// .createDataTypeCharacterString(
// SQLQueryParserFactory.PRIMITIVE_TYPE_CHARACTER_VARYING,
// Integer
// .parseInt(lengthStr),
// null)), null);
// return;
// } else if (fullnameTrimmed.equalsIgnoreCase("cast(null as INT)")) {
// _queryResultSpecification = getSQLQueryParserFactory()
// .createResultColumn(
// getSQLQueryParserFactory()
// .createCastExpression(
// getSQLQueryParserFactory()
// .createNullExpression(),
// "INTEGER"), null);
// return;
// }
// }