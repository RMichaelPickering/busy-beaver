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

import java.util.List;

import org.eclipse.datatools.modelbase.sql.query.QueryValueExpression;

import edu.upenn.cis.orchestra.sql.AbstractSqlFactory;
import edu.upenn.cis.orchestra.sql.IColumnExpression;
import edu.upenn.cis.orchestra.sql.ISqlConstant;
import edu.upenn.cis.orchestra.sql.ISqlDelete;
import edu.upenn.cis.orchestra.sql.ISqlExp;
import edu.upenn.cis.orchestra.sql.ISqlExpression;
import edu.upenn.cis.orchestra.sql.ISqlFromItem;
import edu.upenn.cis.orchestra.sql.ISqlInsert;
import edu.upenn.cis.orchestra.sql.ISqlOrderByItem;
import edu.upenn.cis.orchestra.sql.ISqlParser;
import edu.upenn.cis.orchestra.sql.ISqlSelect;
import edu.upenn.cis.orchestra.sql.ISqlSelectItem;
import edu.upenn.cis.orchestra.sql.ISqlSimpleExpression;
import edu.upenn.cis.orchestra.sql.ISqlUtil;
import edu.upenn.cis.orchestra.sql.ITable;
import edu.upenn.cis.orchestra.sql.ISqlFromItem.Join;
import edu.upenn.cis.orchestra.sql.ISqlOrderByItem.NullOrderType;
import edu.upenn.cis.orchestra.sql.ISqlOrderByItem.OrderType;

/**
 * An <code>ISqlFactory</code> that makes DTP-backed SQL objects.
 * 
 * @author Sam Donnelly
 */
public class DtpSqlFactory extends AbstractSqlFactory {

	/** {@inheritDoc} */
	@Override
	public ISqlDelete newSqlDelete(final String tableName) {
		return new DtpSqlDelete(tableName);
	}

	/** {@inheritDoc} */
	@Override
	public ISqlDelete newDelete(final String tableName, final String alias) {
		return new DtpSqlDelete(tableName, alias);
	}

	/** {@inheritDoc} */
	@Override
	public ISqlExpression newExpression(final ISqlExpression.Code code,
			final ISqlExp o1, final ISqlExp o2) {
		return newExpression(code).addOperand(o1).addOperand(o2);
	}

	/** {@inheritDoc} */
	@Override
	public ISqlExpression newSqlExpression(final ISqlExpression.Code code, List<ISqlExp> exprs) {
		ISqlExpression x = newExpression(code);
		for (ISqlExp i: exprs)
			x = x.addOperand(i);
		
		return x;
	}

	/** {@inheritDoc} */
	@Override
	public ISqlInsert newInsert(final String tableName) {
		return new DtpSqlInsert(tableName);
	}

	/** {@inheritDoc} */
	@Override
	public ISqlInsert newInsert(final String table, final List<String> cols) {
		return new DtpSqlInsert(table, cols);
	}
	
	/** {@inheritDoc} */
	@Override
	public ISqlSelect newSelect() {
		return new DtpSqlSelect();
	}

	/** {@inheritDoc} */
	@Override
	public ISqlSelect newSelect(final ISqlSelectItem selectItem,
			final ISqlFromItem fromItem, final ISqlExpression withItem) {
		return new DtpSqlSelect(selectItem, fromItem, withItem);
	}

	@Override
	public ISqlSelectItem newSelectItem() {
		return new DtpSqlSelectItem();
	}

	/** {@inheritDoc} */
	@Override
	public ISqlSelectItem newSelectItem(final String fullName) {
		return new DtpSqlSelectItem(fullName);
	}

	/** {@inheritDoc} */
	@Override
	public ISqlExpression newExpression(final ISqlExpression.Code code) {
		switch (code) {
		case AVG:
		case COUNT:
		case MAX:
		case MIN:
		case SUM:
			return new ValueExpressionFunctionExpression(code);
		case EXCEPT:
			return new QueryCombinedExpression();
		case EXISTS:
			return new PredicateExistsSqlExpression();
		case NOT:
			return new NegateQuerySearchConditionExpression();
		case EQ:
		case NEQ:
		case GT:
		case GTE:
		case LT:
		case LTE:
			return new PredicateBasicSqlExpression(code);
		case AND:
		case OR:
			return new SearchConditionCombinedExpression(code);
		case COMMA:
			return new ValuesRowExpression();
		case LIKE:
			return new PredicateLikeSqlExpression();
		case PLUSSIGN:
		case MINUSSIGN:
		case MULTSIGN:
		case DIVSIGN:
		case PIPESSIGN:
			return new ValueExpressionCombinedExpression(code);
		case LEAST:
			return new ValueExpressionCombinedExpression(code);
		case GREATEST:
			return new ValueExpressionCombinedExpression(code);
		case IS_NULL:
			return new PredicateIsNullSqlExpression();
		default:
			throw new IllegalArgumentException(code + " not supported.");
		}
	}

	/** {@inheritDoc} */
	@Override
	public ISqlExpression newExpression(final ISqlExpression.Code code,
			final ISqlExp o1) {
		return newExpression(code).addOperand(o1);
	}

	/** {@inheritDoc} */
	@Override
	public ISqlFromItem newFromItem(final String fullName) {
		return new DtpSqlFromItem(fullName);
	}

	/** {@inheritDoc} */
	@Override
	public ISqlConstant newConstant(final String value,
			final ISqlConstant.Type type) {
		switch (type) {
		case COLUMNNAME:
			return new ValueExpressionColumnConstant(value);
		default:
			return new ValueExpressionSimpleConstant(value, type);
		}
	}

	/** {@inheritDoc} */
	@Override
	public ISqlFromItem newFromItem(final Join type, final ISqlFromItem left,
			final ISqlFromItem right, final ISqlExp cond) {
		return new DtpSqlFromItem(type, left, right, cond);
	}

	/** {@inheritDoc} */
	@Override
	public ISqlParser newParser() {
		return new DtpSqlParser();
	}

	/** {@inheritDoc} */
	@Override
	public ISqlSelect newSelect(final ISqlSelectItem selectItem,
			final ISqlFromItem fromItem) {
		return new DtpSqlSelect(selectItem, fromItem);
	}

	/** {@inheritDoc} */
	@Override
	public ISqlDelete newDelete(final ITable table) {
		return new DtpSqlDelete(table);
	}

	/** {@inheritDoc} */
	@Override
	public ISqlInsert newInsert(final ITable table) {
		return new DtpSqlInsert(table);
	}

	/** {@inheritDoc} */
	@Override
	public IColumnExpression newColumnExpression(final String column) {
		return new DtpColumnExpression(column);
	}

	/** {@inheritDoc} */
	@Override
	public ITable newTable(final String table) {
		return new DtpTable(table);
	}

	/** {@inheritDoc} */
	@Override
	public ITable newTable(final String tableName, final String alias) {
		return new DtpTable(tableName, alias);
	}

	/** {@inheritDoc} */
	@Override
	public ISqlUtil newSqlUtils() {
		return new DtpSqlUtil();
	}

	/** {@inheritDoc} */
	@Override
	public ISqlExpression newFalseExpression() {
		return newExpression(ISqlExpression.Code.EQ, newConstant("1",
				ISqlConstant.Type.NUMBER), newConstant("2",
				ISqlConstant.Type.NUMBER));
	}

	/** {@inheritDoc} */
	@Override
	public ISqlOrderByItem newOrderByItem(ISqlConstant orderByName) {
		@SuppressWarnings("unchecked")
		AbstractSQLQueryObject<QueryValueExpression> expression = (AbstractSQLQueryObject<QueryValueExpression>) orderByName;
		return new DtpSqlOrderByItem(expression.getSQLQueryObject());
	}

	/** {@inheritDoc} */
	@Override
	public ISqlOrderByItem newOrderByItem(ISqlConstant orderByName,
			OrderType orderType, NullOrderType nullOrderType) {
		@SuppressWarnings("unchecked")
		AbstractSQLQueryObject<QueryValueExpression> expression = (AbstractSQLQueryObject<QueryValueExpression>) orderByName;
		return new DtpSqlOrderByItem(expression.getSQLQueryObject(), orderType,
				nullOrderType);
	}

	/** {@inheritDoc} */
	@Override
	public ISqlExpression newExpression(final String functionName,
			final ISqlExp... operands) {
		final ISqlExpression sqlExpression = new ValueExpressionFunctionExpression(
				functionName);
		for (ISqlExp operand : operands) {
			sqlExpression.addOperand(operand);
		}
		return sqlExpression;
	}

	/** {@inheritDoc} */
	@Override
	public ISqlSimpleExpression newSimpleExpression(final String value) {
		return new ValueExpressionSimpleExpression().setValue(value);
	}
}
