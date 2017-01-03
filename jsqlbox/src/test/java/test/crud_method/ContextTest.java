package test.crud_method;

import static com.github.drinkjava2.jsqlbox.SqlHelper.q;

import javax.sql.DataSource;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.github.drinkjava2.BeanBox;
import com.github.drinkjava2.jsqlbox.SqlBox;
import com.github.drinkjava2.jsqlbox.Dao;
import com.github.drinkjava2.jsqlbox.SqlBoxContext;
import com.zaxxer.hikari.HikariDataSource;

import test.config.JBeanBoxConfig.DataSourceBox;
import test.config.TestPrepare;
import test.config.po.DB;
import test.config.po.User;

/**
 * This is to test jSqlBoxContext class
 *
 * @author Yong Zhu
 *
 * @version 1.0.0
 * @since 1.0.0
 */
public class ContextTest {

	@Before
	public void setup() {
		TestPrepare.prepareDatasource_SetDefaultSqlBoxConetxt_RecreateTables();
	}

	@After
	public void cleanUp() {
		TestPrepare.closeDatasource_CloseDefaultSqlBoxConetxt();
	}

	/**
	 * Demo how to create context and use it
	 */
	@Test
	public void insertUser1() {
		HikariDataSource ds = new HikariDataSource();// Datasource pool setting
		DataSourceBox dsSetting = new DataSourceBox();
		ds.setUsername((String) dsSetting.getProperty("username"));
		ds.setPassword((String) dsSetting.getProperty("password"));
		ds.setJdbcUrl((String) dsSetting.getProperty("jdbcUrl"));
		ds.setDriverClassName((String) dsSetting.getProperty("driverClassName"));

		SqlBoxContext ctx = new SqlBoxContext(ds, DB.class);// create a new context

		User u = ctx.createEntity(User.class);
		// Assert.assertNotEquals(Dao.getDefaultContext(), u.box().getContext());
		//TODO to fix this bug

		u.setUserName("User1");
		u.setAddress("Address1");
		u.setPhoneNumber("111");
		u.setAge(10);
		u.insert();
		u.box().configTableAlias("a");
		Assert.assertEquals(111, (int) Dao.queryForInteger("select ", u.phoneNumber(), " from ", u.table(), " where ",
				u.userName(), "=", q("User1")));
		ds.close();
	}

	// CtxBox is a SqlBoxContent singleton
	public static class AnotherSqlBoxContextBox extends BeanBox {
		public SqlBoxContext create() {
			SqlBoxContext ctx = new SqlBoxContext();
			ctx.setDataSource((DataSource) BeanBox.getBean(DataSourceBox.class));
			ctx.setDbClass(DB.class);
			return ctx;
		}
	}

	/**
	 * Demo how to use IOC tool like BeanBox to create a context
	 */
	// @Test
	public void insertFromAntoherContext() {
		SqlBoxContext ctx = BeanBox.getBean(AnotherSqlBoxContextBox.class);
		User u = ctx.createEntity(User.class);
		Assert.assertNotEquals(Dao.getDefaultContext(), u.box().getContext());
		u.setUserName("User1");
		u.setAddress("Address1");
		u.setPhoneNumber("111");
		u.setAge(10);
		u.insert();
		Assert.assertEquals(111, (int) Dao.queryForInteger("select ", u.phoneNumber(), " from ", u.table(), " where ",
				u.userName(), "=", q("User1")));
	}

	/**
	 * Test dynamic bind context at runtime, first on board, then buy ticket
	 */
	// @Test
	public void dynamicBindContext() {
		Dao.getDefaultContext().setShowSql(true);
		User u = new User();
		u.setUserName("User1");

		SqlBoxContext ctx = BeanBox.getBean(AnotherSqlBoxContextBox.class);
		SqlBox box = new SqlBox(ctx);
		box.configTable("Users2");
		box.configColumnName("userName", "address");
		ctx.bind(u, box);
		u.insert();
		Assert.assertEquals("User1", Dao.queryForString("select ", u.address(), " from ", u.table(), " where ",
				u.userName(), "=", q("User1")));
		Assert.assertNotEquals(Dao.getDefaultContext(), u.box().getContext());

		SqlBox box2 = ctx.findAndBuildSqlBox(User.class);
		box2.configColumnName("userName", "address");
		ctx.bind(u, box2);
		u.insert();
		Dao.getDefaultContext().setShowSql(true);
		Assert.assertEquals("User1", Dao.queryForString("select ", u.address(), " from ", u.table(), " where ",
				u.userName(), "=", q("User1")));
		Assert.assertNotEquals(Dao.getDefaultContext(), u.box().getContext());
	}

}