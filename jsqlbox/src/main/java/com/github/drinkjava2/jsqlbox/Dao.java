/**
 * Copyright (C) 2016 Yong Zhu.
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
package com.github.drinkjava2.jsqlbox;

import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import com.github.drinkjava2.jsqlbox.jpa.Column;
import com.github.drinkjava2.jsqlbox.jpa.GeneratedValue;
import com.github.drinkjava2.jsqlbox.jpa.IdGenerator;
import static com.github.drinkjava2.jsqlbox.SqlBoxException.assureNotNull;

/**
 * jSQLBox is a macro scale persistence tool for Java 7 and above.
 * 
 * @author Yong Zhu (Yong9981@gmail.com)
 * @version 1.0.0
 * @since 1.0.0
 */

@SuppressWarnings({ "rawtypes", "unchecked" })
public class Dao {
	private static final SqlBoxLogger log = SqlBoxLogger.getLog(Dao.class);
	private SqlBox sqlBox;

	// In future version may delete JDBCTemplate and only use pure JDBC

	private Object bean; // Entity Bean Instance

	public Dao(SqlBoxContext ctx) {
		if (ctx == null)
			SqlBoxException.throwEX("Dao create error, SqlBoxContext  can not be null");
		else if (ctx.getDataSource() == null)
			SqlBoxException.throwEX("Dao create error,  dataSource can not be null");
		SqlBox sb = new SqlBox(ctx);
		this.sqlBox = sb;
	}

	public Dao(SqlBox sqlBox) {
		if (sqlBox == null)
			SqlBoxException.throwEX("Dao create error, sqlBox can not be null");
		else if (sqlBox.getContext() == null)
			SqlBoxException.throwEX("Dao create error, sqlBoxContext can not be null");
		else if (sqlBox.getContext().getDataSource() == null)
			SqlBoxException.throwEX("Dao create error, dataSource can not be null");
		this.sqlBox = sqlBox;
	}

	/**
	 * Get default Dao
	 */
	public static Dao getDao(Object bean, Dao dao) {
		if (dao != null)
			return dao;
		SqlBoxContext ctx = SqlBoxContext.getDefaultSqlBoxContext();
		SqlBox box = ctx.findAndBuildSqlBox(bean.getClass());
		box.beanInitialize(bean);
		Dao d = new Dao(box);
		d.setBean(bean);
		try {
			Method m = bean.getClass().getMethod("putDao", new Class[] { Dao.class });
			m.invoke(bean, new Object[] { d });
		} catch (Exception e) {
			SqlBoxException.throwEX(e, "Dao getDao error for bean \"" + bean + "\", no putDao method found");
		}
		return d;
	}

	/**
	 * Get default Dao
	 */
	public static Dao dao() {
		SqlBoxContext ctx = SqlBoxContext.getDefaultSqlBoxContext();
		SqlBox box = new SqlBox(ctx);
		return new Dao(box);
	}

	// ========JdbcTemplate wrap methods begin============
	// Only wrap some common used JdbcTemplate methods
	public Integer queryForInteger(String... sql) {
		return this.queryForObject(Integer.class, sql);
	}

	/**
	 * Return String type query result, sql be translated to prepared statement
	 */
	public String queryForString(String... sql) {
		return this.queryForObject(String.class, sql);
	}

	/**
	 * Return Object type query result, sql be translated to prepared statement
	 */
	public <T> T queryForObject(Class<?> clazz, String... sql) {
		try {
			SqlAndParameters sp = SqlHelper.splitSQLandParameters(sql);
			logSql(sp);
			if (sp.getParameters().length != 0)
				return (T) getJdbc().queryForObject(sp.getSql(), sp.getParameters(), clazz);
			else
				return (T) getJdbc().queryForObject(sp.getSql(), clazz);
		} finally {
			SqlHelper.clearLastSQL();
		}
	}

	/**
	 * Cache SQL in memory for executeCachedSQLs call, sql be translated to prepared statement
	 * 
	 * @param sql
	 */
	public void cacheSQL(String... sql) {
		SqlHelper.cacheSQL(sql);
	}

	// ========JdbcTemplate wrap methods End============

	/**
	 * Execute sql and return how many record be affected, sql be translated to prepared statement<br/>
	 * Return -1 if no parameters sql executed<br/>
	 * 
	 */
	public Integer execute(String... sql) {
		try {
			SqlAndParameters sp = SqlHelper.splitSQLandParameters(sql);
			logSql(sp);
			if (sp.getParameters().length != 0)
				return getJdbc().update(sp.getSql(), (Object[]) sp.getParameters());
			else {
				getJdbc().execute(sp.getSql());
				return -1;
			}
		} finally {
			SqlHelper.clearLastSQL();
		}
	}

	/**
	 * Execute sql without exception threw, return -1 if no parameters sql executed, return -2 if exception found
	 */
	public Integer executeQuiet(String... sql) {
		try {
			return execute(sql);
		} catch (Exception e) {
			SqlBoxException.eatException(e);
			return -2;
		}
	}

	/**
	 * Transfer cached SQLs to Prepared Statement and batch execute these SQLs
	 */
	public void executeCachedSQLs() {
		try {
			List<List<SqlAndParameters>> subSPlist = SqlHelper.getSQLandParameterSubList();
			logCachedSQL(subSPlist);
			for (final List<SqlAndParameters> splist : subSPlist) {
				getJdbc().batchUpdate(SqlHelper.getSqlForBatch().get(), new BatchPreparedStatementSetter() {
					@Override
					public void setValues(PreparedStatement ps, int i) throws SQLException {
						SqlAndParameters sp = splist.get(i);
						int index = 1;
						for (Object parameter : sp.getParameters()) {
							ps.setObject(index++, parameter);
						}
					}

					@Override
					public int getBatchSize() {
						return splist.size();
					}
				});
			}
		} finally {
			SqlHelper.clearBatchSQLs();
		}
	}

	// ========JdbcTemplate wrap methods End============

	// ========Dao query/crud methods begin=======
	/**
	 * Query and return entity list by sql
	 */
	public List queryEntity(String... sql) {
		return this.queryEntity(this.getBox(), sql);
	}

	/**
	 * Query and return entity list by sql
	 */
	public <T> List<T> queryEntity(Class<?> beanOrSqlBoxClass, String... sql) {
		SqlBox box = this.getBox().getContext().findAndBuildSqlBox(beanOrSqlBoxClass);
		return this.queryEntity(box, sql);
	}

	/**
	 * Query and return entity list by SqlBox and sql
	 */
	private List queryEntity(SqlBox sqlBox, String... sql) {
		if (sqlBox == null)
			throw new SqlBoxException("Dao queryEntity error: sqlBox is null");
		try {
			SqlAndParameters sp = SqlHelper.splitSQLandParameters(sql);
			logSql(sp);
			return getJdbc().query(sp.getSql(), sqlBox.getRowMapper(), sp.getParameters());
		} finally {
			SqlHelper.clearLastSQL();
		}
	}

	/**
	 * Print SQL and parameters to console, usually used for debug <br/>
	 * Use context.setShowSql to control, Default showSql is "false"
	 */
	private void logSql(SqlAndParameters sp) {
		// check if allowed print SQL
		if (!this.getBox().getContext().isShowSql())
			return;
		StringBuilder sb = new StringBuilder(sp.getSql());
		Object[] args = sp.getParameters();
		if (args.length > 0) {
			sb.append("\r\nParameters: ");
			for (int i = 0; i < args.length; i++) {
				sb.append("" + args[i]);
				if (i != args.length - 1)
					sb.append(",");
				else
					sb.append("\r\n");
			}
		}
		log.info(sb.toString());
	}

	/**
	 * Print Cached SQL and parameters, usually used for debug <br/>
	 * Use context.setShowSql to control, Default showSql is "false"
	 */
	private void logCachedSQL(List<List<SqlAndParameters>> subSPlist) {
		if (this.getBox().getContext().isShowSql()) {
			if (subSPlist != null) {
				List<SqlAndParameters> l = subSPlist.get(0);
				if (l != null) {
					SqlAndParameters sp = l.get(0);
					log.info("First Cached SQL:");
					logSql(sp);
				}
			}
			if (subSPlist != null) {
				List<SqlAndParameters> l = subSPlist.get(subSPlist.size() - 1);
				if (l != null) {
					SqlAndParameters sp = l.get(l.size() - 1);
					log.info("Last Cached SQL:");
					logSql(sp);
				}
			}
		}
	}

	/**
	 * Insert a Bean to Database
	 */
	public Integer insert() {// NOSONAR
		if (bean == null)
			SqlBoxException.throwEX("Dao doSave error, bean is null");
		StringBuilder sb = new StringBuilder();
		List<Object> parameters = new ArrayList<>();
		int count = 0;
		sb.append("insert into ").append(sqlBox.getRealTable()).append(" ( ");
		for (Column col : sqlBox.buildRealColumns().values()) {
			if (col.getGeneratedValue() != null) {// ID fields
				GeneratedValue gv = col.getGeneratedValue();
				IdGenerator idgen = this.getBox().getContext().getGenerator(gv);
				assureNotNull(idgen, "IdGenerator can not be null for column \"" + col.getColumnName() + "\"");
				Object id = idgen.getNextID(this.getBox().getContext());
				if (id != null) {
					sb.append(col.getColumnName()).append(",");
					setFieldRealValue(col, id);
					parameters.add(id);
					count++;
				}
			} else if (!col.isPrimeKey() && !SqlBoxUtils.isEmptyStr(col.getColumnName())) {// normal fields
				Object value = getFieldRealValue(col);
				if (value != null) {
					sb.append(col.getColumnName()).append(",");
					if (Boolean.class.isInstance(value)) {// NOSONAR
						if (((Boolean) value).equals(true))
							value = 1;
						else
							value = 0;
					}
					parameters.add(value);
					count++;
				}
			}
		}
		sb.deleteCharAt(sb.length() - 1).append(") ");
		sb.append(SqlHelper.createValueString(count));
		if (this.getBox().getContext().isShowSql())
			logSql(new SqlAndParameters(sb.toString(), parameters.toArray(new Object[parameters.size()])));
		return getJdbc().update(sb.toString(), parameters.toArray(new Object[parameters.size()]));

	}

	/**
	 * Get Field value by it's column defination
	 */
	private Object getFieldRealValue(Column col) {
		String methodName = col.getReadMethodName();
		Method m;
		try {
			m = bean.getClass().getDeclaredMethod(methodName, new Class[] {});// NOSONAR
			return m.invoke(this.bean, new Object[] {});
		} catch (Exception e1) {
			return SqlBoxException.throwEX(e1,
					"Dao getFieldRealValue error, method " + methodName + " invoke error in class " + bean);
		}
	}

	/**
	 * Set Field value by it's column defination
	 */
	private void setFieldRealValue(Column col, Object value) {
		String methodName = col.getWriteMethodName();
		Method m;
		try {
			m = bean.getClass().getDeclaredMethod(methodName, new Class[] { col.getPropertyType() });// NOSONAR
			m.invoke(this.bean, new Object[] { value });
		} catch (Exception e1) {
			SqlBoxException.throwEX(e1, "Dao save error, method " + methodName + " invoke error in class " + bean);
		}
	}

	// ========Dao query/crud methods end=======

	// =============identical methods copied from SqlBox or SqlBoxContext==========
	public String getColumnName() {
		String method1 = Thread.currentThread().getStackTrace()[1].getMethodName();
		String realMethodName = "getColumnName".equals(method1)
				? Thread.currentThread().getStackTrace()[2].getMethodName() : method1;
		return this.getBox().getRealColumnName(realMethodName);
	}

	public String getTable() {
		return this.getBox().getRealTable();
	}

	public void refreshMetaData() {
		this.getContext().refreshMetaData();
	}

	// =============Misc methods end==========

	// ================ Getters & Setters===============
	/**
	 * Return Bean instance which related to this dao
	 */
	public Object getBean() {
		return bean;
	}

	/**
	 * Set a Bean instance related to this dao
	 */
	public void setBean(Object bean) {
		this.bean = bean;
	}

	/**
	 * Return a JdbcTemplate instance<br/>
	 * It's not recommended to use JdbcTemplate directly unless very necessary, JdbcTemplate may be deprecated or
	 * replaced by pure JDBC in future version
	 * 
	 * @return JdbcTemplate
	 */
	public JdbcTemplate getJdbc() {
		return this.getBox().getContext().getJdbc();
	}

	public SqlBox getBox() {
		return sqlBox;
	}

	public SqlBoxContext getContext() {
		return sqlBox.getContext();
	}

	public Object getDatabaseType() {
		return sqlBox.getContext().getDatabaseType();
	}

	public Dao setSqlBox(SqlBox sqlBox) {
		this.sqlBox = sqlBox;
		return this;
	}

}
