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
package edu.upenn.cis.orchestra.exchange.sql;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.upenn.cis.orchestra.Config;
import edu.upenn.cis.orchestra.Debug;
import edu.upenn.cis.orchestra.datalog.DatalogEngine;
import edu.upenn.cis.orchestra.datalog.DatalogSequence;
import edu.upenn.cis.orchestra.datalog.atom.Atom;
import edu.upenn.cis.orchestra.datalog.atom.AtomAnnotation;
import edu.upenn.cis.orchestra.datalog.atom.AtomAnnotationFactory;
import edu.upenn.cis.orchestra.datalog.atom.Atom.AtomType;
import edu.upenn.cis.orchestra.datamodel.IntType;
import edu.upenn.cis.orchestra.datamodel.Mapping;
import edu.upenn.cis.orchestra.datamodel.OrchestraSystem;
import edu.upenn.cis.orchestra.datamodel.Peer;
import edu.upenn.cis.orchestra.datamodel.Relation;
import edu.upenn.cis.orchestra.datamodel.RelationContext;
import edu.upenn.cis.orchestra.datamodel.RelationField;
import edu.upenn.cis.orchestra.datamodel.Schema;
import edu.upenn.cis.orchestra.datamodel.AbstractRelation.BadColumnName;
import edu.upenn.cis.orchestra.datamodel.exceptions.DuplicateRelationIdException;
import edu.upenn.cis.orchestra.datamodel.exceptions.RelationUpdateException;
import edu.upenn.cis.orchestra.datamodel.exceptions.UnsupportedTypeException;
import edu.upenn.cis.orchestra.datamodel.iterators.IteratorException;
import edu.upenn.cis.orchestra.dbms.IDb;
import edu.upenn.cis.orchestra.dbms.SqlDb;
import edu.upenn.cis.orchestra.dbms.sql.generation.SqlTableManipulation;
import edu.upenn.cis.orchestra.exchange.BasicEngine;
import edu.upenn.cis.orchestra.provenance.ProvenanceRelation;
import edu.upenn.cis.orchestra.reconciliation.DbException;
import edu.upenn.cis.orchestra.repository.utils.dbConverter.SchemaConverterStatementsGen;
import edu.upenn.cis.orchestra.util.XMLParseException;


/**
 * Mapping engine based on SQL database
 * 
 * @author zives, gkarvoun
 *
 */
public class SqlEngine extends BasicEngine {
	public static List<Long> delTimes = new ArrayList<Long>();
	public static List<Long> insTimes = new ArrayList<Long>();
	public static List<Long> progTimes = new ArrayList<Long>();
	
	/**
	 * The value used for a labeled null column to indicate that it does not
	 * hold a valid labeled null value.
	 */
	public static final int LABELED_NULL_NONVALUE = 1;
	private static final Logger logger = LoggerFactory.getLogger(SqlEngine.class);

	public SqlEngine(SqlDb d, 
//			IDb updateDb, 
			OrchestraSystem system) throws Exception {
		super(d, 
//				updateDb, 
				system);
	}

	public List<String> getAllBaseTables(final OrchestraSystem syst){
		ArrayList<RelationContext> tables = new ArrayList<RelationContext>();
		tables.addAll(getEdbs());
		tables.addAll(getIdbs());

		ArrayList<String> names = new ArrayList<String>();
		for(RelationContext r : tables){
			names.add(r.getRelation().getFullQualifiedDbId());
		}
		return names;
	}
	
	public static void addLabeledNulls(Relation r) throws BadColumnName {
		int max = r.getNumCols();
		for (int i = 0; i < max; i++) {
			if (r.isNullable(i)) {
				RelationField rf = r.getField(r.getColName(i) + RelationField.LABELED_NULL_EXT);
				
				if (rf == null) {
					int inx = r.getFields().size();
					r.addCol(r.getColName(i) + RelationField.LABELED_NULL_EXT, IntType.INT);
					
					r.getField(inx).setDefaultValueAsString(RelationField.LABELED_NULL_DEFAULT);
				}
			}
		}
	}
	
	/**
	 * Makes mappings and relation definitions consistent, in a variety of ways.
	 * <ul>
	 * <li>Adds any trust annotations.
	 * <li>Creates any missing tables (_L, _R) and synchronize all references to these tables within mappings, atoms, etc.
	 * <li>Ensures consistency of data structures and Java references.
	 * </ul>
	 * 
	 */
	protected void syncTableSchemas(OrchestraSystem system) throws RelationUpdateException {
		
		if (!Config.addTrustAnnotations())
			return;
		
		for (Schema s : system.getAllSchemas())
			s.createMissingRelations();
		
		List<RelationContext> tables = system.getAllSystemRelations();
		
		Map<String, RelationContext> rcMap = new HashMap<String, RelationContext>();
		for (RelationContext rc: tables) {
			if (rc.getRelation().isFinished())
				throw new RelationUpdateException("Oops, " + rc.getRelation().getName() + " is marked as finished!");
			else {
				Debug.println("Extending " + rc.getRelation().getName() + rc.getRelation().getFieldsInList());
				
				
				try {
//					Debug.println("* Adding labeled nulls to " + rc.getRelation().getColumnsAsString());
					addLabeledNulls(rc.getRelation());
//					Debug.println("* After labeled nulls: " + rc.getRelation().getColumnsAsString());
				} catch (BadColumnName e) {
					throw new RelationUpdateException(e.getMessage());
				}

				// Add trust columns ONLY if we have an IDB / non-internal relation...  Elsewhere we will
				// add predefined constants for the trust conditions.
				if (!rc.getRelation().isInternalRelation()) {
					for (Peer p : system.getPeers()) {
						AtomAnnotation ann = AtomAnnotationFactory.createPeerTrustAnnotation(p.getId(), "temp");
						
						try {
							rc.getRelation().addCol(ann.getLabel(), ann.getDataType(), ann.getDefaultTrustValue());
						} catch (BadColumnName e) {
							throw new RelationUpdateException(e.getMessage());
						}
	
					}
				}
				System.out.println("* New schema: " + rc.getRelation().getFieldsInList());
			}
			
			rc.getRelation().markFinished();
			rcMap.put(rc.toString(), rc);
		}

		// Update all relation contexts in the mappings to point to the originals
		for (Mapping m : system.getAllSystemMappings(false)) {
			for (Atom a : m.getMappingHead()) {
				String nam = a.getRelationContext().toString();
				if (rcMap.containsKey(nam))
					a.replaceRelationContext(rcMap.get(nam));
			}
			for (Atom a : m.getBody()) {
				String nam = a.getRelationContext().toString();
				
				if (rcMap.containsKey(nam))
					a.replaceRelationContext(rcMap.get(nam));
			}
		}
		// Update all relation contexts in the mappings to point to the originals
		for (Mapping m : system.getAllSystemMappings(true)) {
			for (Atom a : m.getMappingHead()) {
				String nam = a.getRelationContext().toString();
				if (rcMap.containsKey(nam))
					a.replaceRelationContext(rcMap.get(nam));
			}
			for (Atom a : m.getBody()) {
				String nam = a.getRelationContext().toString();
				
				if (rcMap.containsKey(nam))
					a.replaceRelationContext(rcMap.get(nam));
			}
		}
	}
	
	
	/**
	 * Drops all of the tables corresponding to the DAO
	 * 
	 * @param dao
	 * @return
	 */
	public void dropAllTables() throws IOException {
		final ArrayList<String> operations = dropDbTableStatements(_system);

		Debug.println("Database cleanup -- drops all tables");
		for (final String s: operations) {
			Debug.println(s);
			try {
				if (Config.getApply())
					getMappingDb().evaluateUpdate(s);
			} catch (final Exception e) {
				Debug.println(s + "\nDROP FAILED, probably because table does not exist");
				e.printStackTrace();
//				throw new RuntimeException("Unable to evaluate drop table statement " + e.getStackTrace());
			}
		}
	}

	public void subtractLInsDel() throws Exception {
		final ArrayList<String> operations = subtractLInsDelDbTableStatements(_system);

		Debug.println("Subtract local deletions from insertions");
		for (final String s: operations) {
			Debug.println(s);
			try {
				if (Config.getApply())
					getMappingDb().evaluateUpdate(s);
			} catch (final Exception e) {
				Debug.println(s + "\nSUBTRACT FAILED, probably because table does not exist");
				e.printStackTrace();
//				throw new RuntimeException("Unable to evaluate drop table statement " + e.getStackTrace());
			}
		}
	}

	public void copyBaseTables() throws IOException{
		final ArrayList<String> operations = copyBaseTablesStmts(_system);

//		Store stmts in File
		String str = "copyScript";
//		File dir = new File(Config.getProperty("workdir"));
		File f = new File(str);
		OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f));

		w.append("CONNECT TO " + getMappingDb().getServer() + " USER \"" + 
				getMappingDb().getUsername() + "\" USING \"" + getMappingDb().getPassword() + "\";\n");
		for(String s : operations)
			w.append(s);

		w.append("CONNECT RESET" + ";\n");
		w.close();

//		getMappingDb().evaluateFromShell(str, dir, true);
		Debug.println("COPIED ALL TABLES TO OLD");
	}

	public void compareBaseTablesWithCopies() throws IOException {
		final ArrayList<String> operations = compareBaseTablesStmts(_system);

		Debug.println("Compare latest run with stored/copied");
		boolean diff = false;
		for (final String s: operations) {
			Debug.println(s);
			ResultSet rs = null; 
			try {
				rs = getMappingDb().evaluateQuery(s);
				rs.next();
				int res = rs.getInt(1);
				if(res != 0){
					Debug.println("RUNS NOT EQUAL --- (" + s + ") RETURNED: " + res);
					diff = true;
				}
			} catch (final Exception e) {
				e.printStackTrace();
			} finally { 
				try { 
					if (null != rs) { rs.close(); }
				} catch (SQLException e) { 
					e.printStackTrace();
				}
			}
		}
		if(!diff){
			Debug.println("RUNS ARE EQUAL");
		}
	}

	public void resetConnection(){
		((SqlDb)_mappingDb).resetConnections();
	}

	public void reset() throws IOException, Exception {
		if (!_mappingDb.isConnected())
			_mappingDb.connect();
		_mappingDb.resetCounters();
		clearAllTables();
//		commit();
		finalize();
		_mappingDb.resetCounters();
		((SqlDb)_mappingDb).resetConnections();
//		finalize();
	}

	public void softReset() throws IOException, Exception {
		if (!_mappingDb.isConnected())
			_mappingDb.connect();
		_mappingDb.resetCounters();
		clearAllTables();
//		commit();
		finalize();
		_mappingDb.resetCounters();
//		((SqlDb)_mappingDb).resetConnections();
//		finalize();
	}

	public void clean() throws IOException, Exception {
		if (!_mappingDb.isConnected())
			_mappingDb.connect();
		_mappingDb.resetCounters();
		dropAllTables();
		_mappingDb.resetCounters();
		finalize();
	}

	/** 
	 * @deprecated */
	public void cleanKeepConn() throws IOException, Exception {
		_mappingDb.resetCounters();
		dropAllTables();
//		commit();
		finalize();
		((SqlDb)_mappingDb).resetConnections();
//		finalize();
	}

	public void clearAllTables() throws IOException {
		final ArrayList<String> operations = clearDbTableStatements(_system);

		Debug.println("Database cleanup -- clear all tables");
		for (final String s: operations) {
			Debug.println(s);
			try {
				if (Config.getApply()){
					Debug.println(s);
					getMappingDb().evaluateUpdate(s);
				}
			} catch (final Exception e) {
				Debug.println(s + "\nDELETE FAILED, probably table does not exist");	
//				e.printStackTrace();
//				throw new RuntimeException("Unable to evaluate update" + e.getStackTrace());
			}
		}
	}

	private ArrayList<String> dropDbTableStatements(OrchestraSystem system) 
	throws IOException
	{
		final ArrayList<String> operations = new ArrayList<String>();
		Set<String> names = new HashSet<String>();

		if(Config.getTempTables()){
			getNamesOfAllTablesFromDeltas(/*_deltas,*/ system, true, false, false, names);
		}else{
			getNamesOfAllTablesFromDeltas(/*_deltas,*/ system, true, true, true, names);
		}

		for (final String s : names)
			operations.add(getMappingDb().getSqlTranslator().dropTable(s));

		return operations;
	}

	private ArrayList<String> subtractLInsDelDbTableStatements(OrchestraSystem system) 
	throws IOException
	{
		final ArrayList<String> operations = new ArrayList<String>();

		List<RelationContext> rels = getState().getIdbs(system.getMappingDb().getBuiltInSchemas());

		for (final RelationContext rel : rels){
			String name = rel.getRelation().getFullQualifiedDbId() + "_" + AtomType.INS.toString();
			String name1 = rel.getRelation().getFullQualifiedDbId() + Relation.LOCAL + "_" + AtomType.INS.toString();
			String name2 = rel.getRelation().getFullQualifiedDbId() + Relation.LOCAL + "_" + AtomType.DEL.toString();
			String op1 = "INSERT INTO " + name + " ((SELECT * FROM " + name1 + ") EXCEPT (SELECT * FROM " + name2 + "))"; 
			String op2 = "DELETE FROM " + name1;
			String op3 = "DELETE FROM " + name2;
			String op4 = "INSERT INTO " + name1 + " (SELECT * FROM " + name + ")";
			String op5 = "DELETE FROM " + name;
			operations.add(op1);
			operations.add(op2);
			operations.add(op3);
			operations.add(op4);
			operations.add(op5);
		}

		return operations;
	}


	private ArrayList<String> copyBaseTablesStmts(OrchestraSystem system) 
	{
		final ArrayList<String> operations = new ArrayList<String>();
		Set<String> names = new HashSet<String>();

		getNamesOfAllTablesFromDeltas(/*_deltas,*/ system, false, false, false, names);

		for (final String s : names)
			operations.add(getMappingDb().getSqlTranslator().copyTable(s, s+"OLD"));

		return operations;
	}

	private ArrayList<String> compareBaseTablesStmts(OrchestraSystem system) 
	{
		final ArrayList<String> operations = new ArrayList<String>();
		Set<String> names = new HashSet<String>();

		getNamesOfAllTablesFromDeltas(/*_deltas,*/ system, false, false, false, names);

		for (final String s : names)
			operations.add(getMappingDb().getSqlTranslator().compareTables(s, s+"OLD"));

		return operations;
	}

	public long countAllTables() throws IOException {
		final ArrayList<String> operations = countTableContents(_system);
		int i = 0;

		if (!Config.getApply() //|| !Config.getAutocommit()
		)
			return 0;

		Debug.println("Count the contents of relations");
		long numTuples = 0;
		for (final String s: operations) {

			ResultSet rs = null;
			try {
				i++;
				rs = getMappingDb().evaluateQuery(s);
				rs.next();
				int res = rs.getInt(1);
				Debug.println("(" + s + ") RETURNED: " + res);
				numTuples += res;
			} catch (final Exception e) {
				throw new RuntimeException("Unable to evaluate update " + e.getMessage(), e);
			} finally { 
				try { 
					if (null != rs) { rs.close(); }
				} catch (SQLException e) {
					throw new RuntimeException("Problem closing ResultSet " + e.getMessage(), e);
				}
			}
		}
		logger.debug("# OF BASE AND MAPPING TABLES: " + i);
		return numTuples;
	}

	public long countBaseTables() throws IOException {
		final ArrayList<String> operations = countBaseTableContents(_system);

		if (!Config.getApply() //|| !Config.getAutocommit()
		)
			return 0;

		Debug.println("Count the contents of relations");
		long numTuples = 0;
		for (final String s: operations) {

			ResultSet rs = null;
			try {
				rs = getMappingDb().evaluateQuery(s);
				rs.next();
				int res = rs.getInt(1);
				Debug.println("(" + s + ") RETURNED: " + res);
				numTuples += res;
			} catch (final Exception e) {
				throw new RuntimeException("Unable to evaluate update " + e.getMessage());
			} finally { 
				try { 
					if (null != rs) { rs.close(); }
				} catch (SQLException e) {
					throw new RuntimeException("Problem closing ResultSet " + e.getMessage(), e);
				}
			}
		}
		return numTuples;
	}

	public long prepareNonIncremental() throws IOException {
		final ArrayList<String> operations = new ArrayList<String>();
		Set<String> names = new HashSet<String>();
		BasicEngine.getNamesOfAllTablesFromDeltas(/*_deltas,*/ _system, false, true, false, names);

		for (final String s : names){
//			operations.add("CREATE TABLE " + s + "_TEMP as (select * from " + s + "_INS) WITH NO DATA");
//			operations.add("INSERT INTO " + s + "_TEMP ((select * from " + s + "_INS) except (select * from  " + s + "_DEL))");
//			operations.add("DELETE FROM " + s + "_INS");
//			operations.add("DELETE FROM " + s + "_DEL");
//			operations.add("INSERT INTO " + s + "_INS (select * from " + s + "_TEMP)");
//			operations.add("DROP TABLE " + s + "_TEMP");
			if(s.endsWith("_L")
//					|| s.endsWith("_R")
			){
				operations.add(getMappingDb().getSqlTranslator().subtractTables(s + "_INS", s + "_DEL", "KID"));
				operations.add(getMappingDb().getSqlTranslator().deleteTable(s + "_DEL"));
//				operations.add("DELETE FROM " + s + "_INS R1 WHERE EXISTS (SELECT R2.KID FROM " + s + "_DEL R2 WHERE R2.KID = R1.KID)");
//				operations.add("DELETE FROM " + s + "_DEL R1");
			}
		}

		if (Config.getApply()){
			Debug.println("Prepare for non-incremental");
			Calendar before = Calendar.getInstance();;
			Calendar after = Calendar.getInstance();;
			long time = 0;

//			before = Calendar.getInstance();
			int i = 0;

			try {
				getMappingDb().commit();
				for (final String s: operations) {

					Debug.println(s);
					if((i % 2) == 0)
						before = Calendar.getInstance();
					getMappingDb().evaluateUpdate(s);
					if((i % 2) == 0){
						after = Calendar.getInstance();
						time += (after.getTimeInMillis() - before.getTimeInMillis());
					}
				}
				getMappingDb().commit();
			} catch (final Exception e) {
				throw new RuntimeException("Unable to evaluate update " + e.getMessage());
			}

//			after = Calendar.getInstance();
//			time = after.getTimeInMillis() - before.getTimeInMillis();
			logger.debug("EXP: NON-INCREMENTAL PREPARATION TIME: " + time + " msec");
//			logger.debug("EXP: REAL NON-INCREMENTAL PREPARATION TIME EST: " + time/2 + " msec");
//			return time/2;
			return time;
		}else{
			return 0;
		}
	}

	private ArrayList<String> clearDbTableStatements (OrchestraSystem system) 
	throws IOException
	{
		final ArrayList<String> operations = new ArrayList<String>();

//		OrchestraSystem system = dao.loadAllPeers();
		Set<String> names = new HashSet<String>();


		BasicEngine.getNamesOfAllTablesFromDeltas(/*_deltas,*/ system, true, true, true, names);

		for (final String s : names)
			operations.add("DELETE FROM " + s);

		return operations;
	}

	private ArrayList<String> countTableContents(OrchestraSystem system) 
	throws IOException
	{
		final ArrayList<String> operations = new ArrayList<String>();
		Set<String> names = new HashSet<String>();

		BasicEngine.getNamesOfAllTablesFromDeltas(/*_deltas,*/ system, false, true, true, names);

		for (final String s : names)
			operations.add("select count(*) from " + s);

		return operations;
	}

	private ArrayList<String> countBaseTableContents(OrchestraSystem system) 
	throws IOException
	{
		final ArrayList<String> operations = new ArrayList<String>();
		Set<String> names = new HashSet<String>();

		BasicEngine.getNamesOfAllTablesFromDeltas(/*_deltas,*/ system, false, false, false, names);

		for (final String s : names)
			operations.add("select count(*) from " + s);

		return operations;
	}


	public SqlDb getMappingDb() {
		return (SqlDb)_mappingDb;
	}

	//public CreateProvenanceStorageSql getProvenancePrepInfo() {
	//	return (CreateProvenanceStorageSql)_provenancePrep;
	//}

	public void importUpdates(final IDb sourceDb) throws Exception {
//		final List<String> baseTables = getNamesOfAllTables(_system, false, false, true);
		final List<String> baseTables = getAllBaseTables(_system);

		getMappingDb().finalize();
		getMappingDb().importData(sourceDb, baseTables);
		getMappingDb().commit();
	}

	public long evaluateProvenanceQuery(DatalogSequence ds) throws Exception {
		if (!getMappingDb().isConnected())
			getMappingDb().connect();
		final DatalogEngine de = new DatalogEngine(
				getMappingDb());

//		// Map all of the updates from the other peers into our Exchange
//		// instance
////		for (Peer p2 : _system.getPeers()) {
//		((SqlDb)_system.getMappingDb()).fetchPublishedTransactions(_system, lastrec, recno, reconciler, _system.getRecDb(reconciler.getId()));
////		}
//		// Do the non-incremental maintenance, simply by recomputing the
//		// provenance relations
		logger.debug("EXP: --------------------------------------");

		try {	
			Calendar before;
			Calendar after;
			long time;
			long retTime;

			logger.debug("=====================================================");
			logger.debug("PROVENANCE QUERY");
			logger.debug("=====================================================");

			de.commitAndReset();

			if(de._sql instanceof SqlDb)
				((SqlDb)de._sql).activateRuleBasedOptimizer();

			before = Calendar.getInstance();
			de.evaluatePrograms(ds);
			after = Calendar.getInstance();
			time = after.getTimeInMillis() - before.getTimeInMillis();

			logger.debug("PROVENANCE QUERY ALG TIME (INCL COMMIT): " + time + " msec");
			logger.debug("TIME SPENT FOR COMMIT AND LOGGING DEACTIVATION: " + de.logTime() + " msec");
			logger.debug("EXP: NET PROVENANCE QUERY TIME: " + (time - de.logTime()) + " msec");

			SqlEngine.progTimes.add(new Long(time - de.logTime()));
			retTime = time - de.logTime();

			return retTime;

		} catch (Exception ex) {
			ex.printStackTrace();
			throw ex;
		}
	}
	/**
	 * Execute the deletion, insertion, etc. mappings
	 */
	public long mapUpdates(int lastrec, int recno, Peer reconciler, 
			boolean insFirst) throws Exception {

		final DatalogEngine de = new DatalogEngine(_mappingDb);
		final Calendar before = Calendar.getInstance();

		commit();
		// Map all of the updates from the other peers into our Exchange
		// instance
//		for (Peer p2 : _system.getPeers()) {
		((SqlDb)_system.getMappingDb()).fetchPublishedTransactions(_system, lastrec, recno, reconciler, _system.getRecDb(reconciler.getId()));
//		}
		// Do the non-incremental maintenance, simply by recomputing the
		// provenance relations
		logger.debug("EXP: --------------------------------------");
		if (Config.getNonIncremental()) {
			// TODO: right now we don't apply the deletions and insertions to the base.

//			List<Rule> rFull = new ArrayList<Rule>(_deltas.getOuterUnionMappingRules().size());
//			rFull.addAll(_deltas.getOuterUnionMappingRules()); 
//			RecursiveDatalogProgram p = new RecursiveDatalogProgram(_deltas.getOuterUnionMappingRules());
//			de.evaluateProgram(p);

			long prepTime = prepareNonIncremental();
//			logger.debug("TOTAL NUM OF TUPLES IN BASE AND MAPPING RELATIONS AFTER PREPARE NON-INCREMENTAL: " + countAllTables());

			long execTime = _insertionRules.execute(de);

			SqlEngine.insTimes.set(SqlEngine.insTimes.size()-1, new Long(prepTime + execTime));

			logger.debug("TOTAL NUM OF TUPLES IN BASE AND MAPPING RELATIONS AFTER NON-INCREMENTAL: " + countAllTables());
			logger.debug("TOTAL NUM OF TUPLES IN BASE RELATIONS AFTER NON-INCREMENTAL: " + countBaseTables());

			de.resetCounters();
		} else if (insFirst) {

//			Do incremental insertion
			_insertionRules.execute(de);

			logger.debug("TOTAL NUM OF TUPLES IN BASE AND MAPPING RELATIONS AFTER INSERTIONS: " + countAllTables());
			logger.debug("TOTAL NUM OF TUPLES IN BASE RELATIONS AFTER INSERTIONS: " + countBaseTables());
			de.resetCounters();

			// Do incremental deletion
			
			_deletionRules.execute(de);

			logger.debug("TOTAL NUM OF TUPLES IN BASE AND MAPPING RELATIONS AFTER DELETIONS: " + countAllTables());
			logger.debug("TOTAL NUM OF TUPLES IN BASE RELATIONS AFTER DELETIONS: " + countBaseTables());
			de.resetCounters();
		} else {
			// Do incremental deletion
			_deletionRules.execute(de);

			logger.debug("TOTAL NUM OF TUPLES IN BASE AND MAPPING RELATIONS AFTER DELETIONS: " + countAllTables());
			logger.debug("TOTAL NUM OF TUPLES IN BASE RELATIONS AFTER DELETIONS: " + countBaseTables());
			de.resetCounters();

//			Do incremental insertion
			_insertionRules.execute(de);

			logger.debug("TOTAL NUM OF TUPLES IN BASE AND MAPPING RELATIONS AFTER INSERTIONS: " + countAllTables());
			logger.debug("TOTAL NUM OF TUPLES IN BASE RELATIONS AFTER INSERTIONS: " + countBaseTables());
			de.resetCounters();
		}

		getMappingDb().runStatsOnAllTables(_system);

		finalize();

//		if (Config.DO_APPLY)
//		de.commit();

		final Calendar after = Calendar.getInstance();
		final long time = after.getTimeInMillis() - before.getTimeInMillis();
		Debug.println("TOTAL DELTA TIME: " + time + " msec");

		// TEMPORARY:  apply everything
		((SqlDb)_system.getMappingDb()).acceptPublishedTransactions(_system, lastrec, recno, reconciler, _system.getRecDb(reconciler.getId()));

		return time;
	}
	
	public void repairSchema() throws Exception
	{
		/*
		// Iterate through delta relations, add STRATUM
		if (Config.getStratified()) {
			for (RelationContext rc: _system.getAllSystemRelations()) {
				Relation r = rc.getRelation();
				if (r.getName().endsWith(Relation.INSERT) || r.getName().endsWith(Relation.DELETE)) {
					r.getFields().add(0, new RelationField("STRATUM", "Stratum number", IntType.INT));
					r.getField(0).setDefaultValueAsString("0");
				}
			}
		}*/
		
		if (!_mappingDb.isConnected()) {
			_mappingDb.connect();
		}
		createInternalSchemaRelations();
		createInternalProvenanceRelations();
	}

	/**
	 * Migrates the schema of a database, based on a DAO object 
	 * 
	 * @param dao
	 * @throws IOException
	 * @throws ParseException
	 * @throws SQLException
	 */
	public void migrate() throws Exception
	{
		if (!_mappingDb.isConnected()) {
			_mappingDb.connect();
		}
		Calendar before = Calendar.getInstance();

		System.out.println("Checking relations");
		createInternalSchemaRelations();
		createInternalProvenanceRelations();
		_system.prepareSystemForLocalUpdater();

		try {
			int updates = moveExistingData();
			if (updates != 0) {
				_system.publishAndMap();
			}
			_system.publishAndMap();

		} catch (Exception se) {
			System.err.println("Error importing existing data");
			se.printStackTrace();
//			throw se;
		}


		Calendar after = Calendar.getInstance();
		long time = (after.getTimeInMillis() - before.getTimeInMillis());
		logger.debug("EXP: TOTAL MIGRATE TIME: " + time + " msec");

		finalize();

	}

	public void createBaseSchemaRelations() throws Exception
	{
		final List<String> statements = new ArrayList<String>();

		if (!_mappingDb.isConnected())
			_mappingDb.connect();

		Calendar before = Calendar.getInstance();
		for (final Peer p : _system.getPeers()) {
			for (final Schema sc : p.getSchemas()) {
				final SchemaConverterStatementsGen statementsGen = 
					new SchemaConverterStatementsGen (getMappingDb().getDataSource(), Config.getJDBCDriver(), sc);
				
				statements.addAll(statementsGen.createNecessaryTables( 
						!Config.getAutocommit(), Config.getJDBCDriver(), Config.getStratified(), 
						getMappingDb().getSqlTranslator().getLoggingMsg().length() == 0, getMappingDb()));
//				Config.getLoggingMsg().length() == 0));
				
			}
		}

		logger.debug("+++ Initialization +++");
		for (final String s: statements) {

			try {
				if (Config.getApply())
					getMappingDb().evaluate(s);
				else
					Debug.println(s);
			} catch (final Exception e) {
				Debug.println("Unable to evaluate statement " + s);
				e.printStackTrace();
			}
		}

		Calendar after = Calendar.getInstance();
		long time = (after.getTimeInMillis() - before.getTimeInMillis());
		logger.debug("EXP: INIT TIME: " + time + " msec");

	}


	public void createInternalProvenanceRelations () 
	throws IOException, SQLException, ClassNotFoundException, UnsupportedTypeException
	{

		Calendar before = Calendar.getInstance();

//		createProvenanceTables(getState().getMappingRelations());
		List<String> statements = new ArrayList<String>();

		statements.addAll(createProvenanceTables(getState().getRealMappingRelations()));
		statements.addAll(createProvenanceTables(getState().getInnerJoinRelations()));
		statements.addAll(createProvenanceTables(getState().getSimulatedOuterJoinRelations()));
		statements.addAll(createProvenanceTables(getState().getRealOuterJoinRelations()));
		statements.addAll(createProvenanceTables(getState().getOuterUnionRelations()));

		System.out.println("+++ Provenance +++");
		for (final String s: statements) {

			try {
				if (!s.isEmpty()) {
					logger.debug("Evaluating: {}", s);
					if (Config.getApply()) {
						getMappingDb().evaluate(s);
					}
				}
			} catch (final Exception e) {
				Debug.println("Unable to evaluate statement " + s);
				e.printStackTrace();
			}
		}
		Calendar after = Calendar.getInstance();
		long time = (after.getTimeInMillis() - before.getTimeInMillis());
		System.out.println("EXP: MIGRATE PROVENANCE RELATIONS TIME: " + time + " msec");
	}
	
	public void createInternalSchemaRelations () 
	throws IOException, XMLParseException, SQLException, ClassNotFoundException
	{
		final List<String> statements = new ArrayList<String>();
//		final Map<AbstractRelation,List<String>> map = new HashMap<AbstractRelation,List<String>>();

		Calendar before = Calendar.getInstance();
		for (final Peer p : _system.getPeers())
			for (final Schema sc : p.getSchemas()) {
				statements.addAll(createPeerRelations(sc));
				/*
				try {
					final SchemaConverterStatementsGen statementsGen = 
						new SchemaConverterStatementsGen (getMappingDb().getDataSource(), Config.getJDBCDriver(), sc);

					statements.addAll(statementsGen.createNecessaryTables(!Config.getAutocommit(), Config.getJDBCDriver(), Config.getStratified(), 
							getMappingDb().getSqlTranslator().getLoggingMsg().length() == 0, getMappingDb()));

//					List<SqlStatement> tableConversionStatements = statementsGen.createTableConversionStatements(statements, 
//					!Config.getAutocommit(), Config.getJDBCDriver(), Config.getStratified(), getMappingDb().getSqlTranslator().getLoggingMsg()));

					map.putAll(statementsGen.createTableConversionStatements(statements, 
							!Config.getAutocommit(), Config.getJDBCDriver(), Config.getStratified(), 
							getMappingDb().getSqlTranslator().getLoggingMsg(), getMappingDb(), _system.isBidirectional()));
				} catch (MetaDataAccessException e) {
					e.printStackTrace();
					throw new XMLParseException("Unable to access SQL");
				}*/
			}

		logger.debug("+++ Migration +++");
		for (final String s: statements) {

			try {
				if (!s.isEmpty()) {
					//logger.debug
					Debug.println("Evaluating: " + s);
					if (Config.getApply()) {
						getMappingDb().evaluate(s);
					}
				}
			} catch (final Exception e) {
				Debug.println("Unable to evaluate statement " + s);
				e.printStackTrace();
			}
		}

		Calendar after = Calendar.getInstance();
		long time = (after.getTimeInMillis() - before.getTimeInMillis());
		logger.debug("EXP: MIGRATE/REPAIR RELATIONS TIME: " + time + " msec");

	}

	/**
	 * Transfers data from the original table to the local insertions table
	 * and imports it into the update store
	 * @return number of moved tuples
	 * 
	 * @throws IOException
	 * @throws SQLException
	 * @throws ClassNotFoundException
	 * @throws DbException 
	 * @throws IteratorException 
	 * @throws DuplicateRelationIdException 
	 */
	public int moveExistingData () 
	throws IOException, SQLException, ClassNotFoundException, DbException, IteratorException, DuplicateRelationIdException
	{
		int updates = 0;
		for (final Peer p : _system.getPeers())
			for (final Schema sc : p.getSchemas()) {
				for (final Relation r : sc.getRelations()) {

					// Move data from the original x relation to the x_L_INS relation
					if (!r.isInternalRelation()) {
						//updates += getMappingDb().convertTuplesToIndependentTransactions(p, sc, r, _system.getRecDb(p.getPeerId().getID()));
						updates += getMappingDb().moveExistingData(r, "", Relation.LOCAL + "_INS");
					}
				}
			}
		return updates;
	}

	@Override
	protected List<String> createProvenanceTables(List<RelationContext> mappingRels) {
		List<String> toApply = new ArrayList<String>();
//		if(_provenancePrep == null)
//		_provenancePrep = createProvenanceStorage();

		for(RelationContext relCtx : mappingRels){

			ProvenanceRelation rel = (ProvenanceRelation)relCtx.getRelation();
			
//			if(!rel.getMappings().get(0).isFakeMapping()){
			if(Config.getOuterUnion()){
				toApply.addAll(SqlTableManipulation.createOuterUnionProvenanceDbTableSet(rel, !Config.getAutocommit(), getMappingDb()));
			}else{
				toApply.addAll(SqlTableManipulation.createProvenanceDbTableSet(rel, !Config.getAutocommit(), getMappingDb(), _system.isBidirectional()));
			}
//			}
		}

		return toApply;
	}
	
	protected List<String> createPeerRelations(Schema sc) {
		List<String> toApply = new ArrayList<String>();
		for (Relation rel : sc.getRelations()) {
			toApply.addAll(SqlTableManipulation.createAuxiliaryDbTableSet(rel, !Config.getAutocommit(), getMappingDb()));
		}
		
		
		return toApply;
	}
}


