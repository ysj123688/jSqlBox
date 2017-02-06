
package test.coverage_test;

import static com.github.drinkjava2.jsqlbox.MappingHelper.manyToMany;
import static com.github.drinkjava2.jsqlbox.MappingHelper.mapping;
import static com.github.drinkjava2.jsqlbox.MappingHelper.oneToMany;
import static com.github.drinkjava2.jsqlbox.MappingHelper.oneToOne;
import static com.github.drinkjava2.jsqlbox.MappingHelper.tree;
import static com.github.drinkjava2.jsqlbox.SqlHelper.from;
import static com.github.drinkjava2.jsqlbox.SqlHelper.select;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.github.drinkjava2.jsqlbox.Dao;
import com.github.drinkjava2.jsqlbox.Mapping;
import com.github.drinkjava2.jsqlbox.SqlAndParameters;
import com.github.drinkjava2.jsqlbox.SqlBoxUtils;
import com.github.drinkjava2.jsqlbox.SqlHelper;

import test.config.PrepareTestContext;
import test.config.po.Customer;
import test.config.po.Order;
import test.config.po.OrderItem;

public class SqlMappingTest {
	@Before
	public void setup() {
		System.out.println("=============================Testing SqlBoxTest=============================");
		PrepareTestContext.prepareDatasource_setDefaultSqlBoxConetxt_recreateTables();
		Dao.executeQuiet("drop table orderitem");
		Dao.executeQuiet("drop table orders");
		Dao.executeQuiet("drop table customer");
		Dao.execute(Customer.CREATE_SQL);
		Dao.execute(Order.CREATE_SQL);
		Dao.execute(OrderItem.CREATE_SQL);
		Dao.refreshMetaData();
	}

	@After
	public void cleanUp() {
		PrepareTestContext.closeDatasource_closeDefaultSqlBoxConetxt();
	}

	/**
	 * Coverage test of mapping method only
	 */
	@Test
	public void prepareSQLandParameters() {
		Customer c = new Customer();
		Order o = new Order();
		OrderItem i = new OrderItem();
		SqlAndParameters sp = SqlHelper.prepareSQLandParameters(select(), c.all(), ",", o.all(), ",", i.all(), from(),
				c.table(), //
				" left outer join ", o.table(), " on ", mapping(oneToOne(), c.ID(), "=", o.CUSTOMERID()), //
				" left outer join ", o.table(), " on ", mapping(oneToMany(), c.ID(), "=", o.CUSTOMERID(), c.ID()), //
				" left outer join ", i.table(), " on ",
				mapping(manyToMany(), o.ID(), "=", i.ORDERID(), c.ID(), "newfield"), //
				" left outer join ", i.table(), " on ", mapping(tree(), o.ID(), "=", i.ORDERID(), o.ID(), i.ORDERID()), //
				" order by ", o.ID(), ",", i.ID());
		System.out.println(SqlBoxUtils.formatSQL(sp.getSql()));
		List<Mapping> l = sp.getMappingList();
		for (Mapping mapping : l) {
			System.out.println(mapping.getDebugInfo());
		}
	}

}
