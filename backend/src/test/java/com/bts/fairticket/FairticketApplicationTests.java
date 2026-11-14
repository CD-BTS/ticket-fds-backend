package com.bts.fairticket;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("mysql")
@SpringBootTest
class FairticketApplicationTests {

	@Autowired
	private DataSource dataSource;

	@Test
	void connectsToMySql() throws Exception {
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery("SELECT 1")) {
			assertEquals("MySQL", connection.getMetaData().getDatabaseProductName());
			assertTrue(result.next());
			assertEquals(1, result.getInt(1));
		}
	}

}
