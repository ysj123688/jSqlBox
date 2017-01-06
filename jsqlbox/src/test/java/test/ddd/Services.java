package test.ddd;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.github.drinkjava2.AopAround;
import com.github.drinkjava2.BeanBox;
import com.github.drinkjava2.jsqlbox.Dao;
import com.github.drinkjava2.jsqlbox.SqlBoxContext;

import test.config.JBeanBoxConfig.SpringTxInterceptorBox;
import test.config.TestPrepare;

public class Services {

	@Before
	public void setup() {
		System.out.println("===============================Testing Services===============================");
		TestPrepare.prepareDatasource_setDefaultSqlBoxConetxt_recreateTables();
	}

	@After
	public void cleanUp() {
		TestPrepare.closeDatasource_closeDefaultSqlBoxConetxt();
	}

	/**
	 * This method is wrapped by Spring's declarative transaction
	 */
	@AopAround(SpringTxInterceptorBox.class)
	public void receivePartsFromPO(PODetail poDetail, Integer receiveQTY) {
		PODetail.receivePartsFromPO(poDetail, receiveQTY);
	}

	@Test
	public void doTest() {
		SqlBoxContext.getDefaultSqlBoxContext().setShowSql(true);

		// drop and recreate tables;
		// Already tested on H2, MySql, Oracle. For MySql need set InnoDB to support transaction
		String innoDB = Dao.getDefaultDatabaseType().isMySql() ? "ENGINE=InnoDB DEFAULT CHARSET=utf8;" : "";
		Dao.executeQuiet("drop table part");
		Dao.execute(Part.CREATE_SQL + innoDB);
		Dao.executeQuiet("drop table podetail");
		Dao.execute(PODetail.CREATE_SQL + innoDB);
		Dao.executeQuiet("drop table poreceiving");
		Dao.execute(POReceiving.CREATE_SQL + innoDB);
		Dao.executeQuiet("drop table logpart");
		Dao.execute(LogPart.CREATE_SQL + innoDB);
		Dao.refreshMetaData();

		// fill test data
		Part part = Part.insert("part1", 0);
		PODetail poDetail = PODetail.insert("po1", part.getPartID(), 1);
		System.out.println("======Here start do the services test========");

		// do test
		Services service = BeanBox.getBean(Services.class);
		service.receivePartsFromPO(poDetail, 1);

		// Check result
		Part partCheck = Dao.load(Part.class, part.getPartID());
		Assert.assertEquals(1, (int) partCheck.getTotalCurrentStock());
		Assert.assertEquals(1, (int) partCheck.getStockAvailable());

		PODetail poDetailCheck = Dao.load(PODetail.class, poDetail.getId());
		Assert.assertEquals(1, (int) poDetailCheck.getReceived());
		Assert.assertEquals(0, (int) poDetailCheck.getBackOrder());

		Assert.assertEquals(2, (int) Dao.queryForInteger("select count(*) from logpart"));

	}
}