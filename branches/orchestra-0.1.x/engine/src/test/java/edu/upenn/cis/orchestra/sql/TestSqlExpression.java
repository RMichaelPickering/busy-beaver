/*
 * Copyright (C) 2010 Trustees of the University of Pennsylvania
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS of ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.upenn.cis.orchestra.sql;

import org.testng.Assert;
import org.testng.annotations.Test;
import static edu.upenn.cis.orchestra.TestUtil.FAST_TESTNG_GROUP;

/**
 * Test the class returned by {@code
 * SqlFactories.getSqlFactory.newSqlExpression(...)}.
 * 
 * @author Sam Donnelly
 */
@Test(groups = FAST_TESTNG_GROUP)
public class TestSqlExpression {

	/**
	 * Test method for {@code
	 * ISqlFactory.newSqlExpression(ISqlExpression.Code.NOT, someSqlExpression)}
	 * .
	 */
	@Test
	public void negatedNewSqlExpression() {
		ISqlFactory sqlFactory = SqlFactories.getSqlFactory();

		ISqlExpression whereExpression = sqlFactory
				.newSqlExpression(ISqlExpression.Code.EQ);
		whereExpression.addOperand(
				SqlFactories.getSqlFactory().newSqlConstant("44",
						ISqlConstant.Type.NUMBER)).addOperand(
				SqlFactories.getSqlFactory().newSqlConstant("45",
						ISqlConstant.Type.NUMBER));
		ISqlExpression notExpression = sqlFactory.newSqlExpression(
				ISqlExpression.Code.NOT, whereExpression);
		Assert.assertEquals(SqlUtil.stripWhiteSpaceAndComments(notExpression
				.toString()), SqlUtil
				.stripWhiteSpaceAndComments("(NOT (44 = 45))"));
	}
}
