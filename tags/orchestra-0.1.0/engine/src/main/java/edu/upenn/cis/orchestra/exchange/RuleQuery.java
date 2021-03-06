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
package edu.upenn.cis.orchestra.exchange;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import edu.upenn.cis.orchestra.dbms.IDb;
import edu.upenn.cis.orchestra.mappings.Rule;


/**
 * Basic core container for the query statements
 * generated by RuleCodeGen
 * 
 * @author zives
 *
 */
public abstract class RuleQuery {
	public RuleQuery(IDb db) {
		_isPrepared = false;
		_db = db;
		_statements = new ArrayList<String>();
	}

	/**
	 * The database in which the query
	 * is to be executed
	 * 
	 * @return
	 */
	protected IDb getDatabase() {
		return _db;
	}
	
	/**
	 * hint for setting parameterized stratified fields in prepared statements
	 */
	public abstract void setPreparedParams(List<List<Integer>> l);
	
	/**
	 * Number of statements
	 * 
	 * @return
	 */
	public int size() {
		return _statements.size();
	}
	
	/**
	 * Getter for a particular statement
	 * 
	 * @param i
	 * @return
	 */
	public String get(int i) {
		return _statements.get(i);
	}
	
	/**
	 * Some sort of prepared/precompiled statement?
	 * @return
	 */
	public boolean isPrepared() {
		return _isPrepared;
	}
	
	/**
	 * Basic routine to evaluate the statement in the DB
	 * 
	 * @return
	 */
	public abstract int evaluateSelf(String queryString, int curIterCnt, List<Rule> rules, Map<Rule, List<String>> bodyTables, Map<Rule, List<String>> headTables);
	
	/**
	 * Request to prepare/precompile (may fail)
	 * 
	 * @return
	 * @throws Exception
	 */
	public abstract boolean prepare() throws Exception;
	
	/**
	 * Add another statement
	 * 
	 * @param statement
	 */
	public void add(String statement) {
		_statements.add(statement);
	}
	
	/**
	 * Add a prepared statement with params
	 * 
	 * @param statement
	 */
	public abstract void addPrepared(String statement, List<Integer> params);
	
	
	protected List<String> _statements;
	IDb _db;
	protected boolean _isPrepared;
	
	public abstract void cleanupPrepared();
}
