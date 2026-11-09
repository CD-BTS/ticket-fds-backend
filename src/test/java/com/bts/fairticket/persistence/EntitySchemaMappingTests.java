package com.bts.fairticket.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Table;
import org.junit.jupiter.api.Test;

import com.bts.fairticket.behavior.entity.BehaviorEvent;
import com.bts.fairticket.credit.entity.CreditHistory;
import com.bts.fairticket.credit.entity.UserCredit;
import com.bts.fairticket.policy.entity.PolicyDecision;
import com.bts.fairticket.risk.entity.RiskAssessment;
import com.bts.fairticket.user.entity.UserAccount;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// 제공된 SQL과 Hibernate 메타데이터를 비교한다. DB 연결이나 DDL 실행은 하지 않는다.
class EntitySchemaMappingTests {

	private static final Pattern TABLE_PATTERN = Pattern.compile(
			"CREATE TABLE `(\\w+)` \\((.*?)\\)(?: ENGINE[^;]*)?;", Pattern.DOTALL);
	private static final Pattern COLUMN_PATTERN = Pattern.compile(
			"^`(\\w+)` (varchar\\(\\d+\\)|bigint|int|double|datetime|json)(.*)$");
	private static final Pattern INDEX_PATTERN = Pattern.compile(
			"CREATE (UNIQUE )?INDEX `(\\w+)` ON `(\\w+)` \\(([^;]+)\\);");
	private static final Pattern FOREIGN_KEY_PATTERN = Pattern.compile(
			"ALTER TABLE `(\\w+)` ADD FOREIGN KEY \\(`(\\w+)`\\) REFERENCES `(\\w+)` \\(`(\\w+)`\\);");
	private static final List<Class<?>> ENTITIES = List.of(
			UserAccount.class, BehaviorEvent.class, UserCredit.class,
			CreditHistory.class, RiskAssessment.class, PolicyDecision.class,
			com.bts.fairticket.concert.entity.Concert.class);

	@Test
	void mappingsMatchProvidedMySqlSchemaWithoutDatabaseConnection() throws Exception {
		String sql = Files.readString(Path.of("db/schema.sql"));
		try (var registry = new StandardServiceRegistryBuilder()
				.applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
				.applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
				.applySetting("hibernate.hbm2ddl.auto", "none")
				.build()) {
			MetadataSources sources = new MetadataSources(registry);
			ENTITIES.forEach(sources::addAnnotatedClass);
			Metadata metadata = sources.buildMetadata();
			Map<String, Table> tables = new LinkedHashMap<>();
			metadata.getEntityBindings().forEach(entity ->
					tables.put(entity.getTable().getName(), entity.getTable()));

			var tableMatcher = TABLE_PATTERN.matcher(sql);
			int tableCount = 0;
			while (tableMatcher.find()) {
				tableCount++;
				String tableName = tableMatcher.group(1);
				Table table = tables.get(tableName);
				assertNotNull(table, tableName);
				Map<String, Column> columns = table.getColumns().stream()
						.collect(Collectors.toMap(Column::getName, column -> column));
				int columnCount = 0;
				for (String line : tableMatcher.group(2).split("\\R")) {
					var matcher = COLUMN_PATTERN.matcher(line.trim());
					if (!matcher.matches()) {
						continue;
					}
					columnCount++;
					String columnName = matcher.group(1);
					String definition = matcher.group(3);
					Column column = columns.get(columnName);
					assertNotNull(column, tableName + "." + columnName);
					assertEquals(normalizeType(matcher.group(2)), normalizeType(column.getSqlType(metadata)),
							tableName + "." + columnName + " type");
					assertEquals(!definition.contains("NOT NULL") && !definition.contains("PRIMARY KEY"),
							column.isNullable(), tableName + "." + columnName + " nullability");
					if (definition.contains("PRIMARY KEY")) {
						assertEquals(List.of(columnName), columnNames(table.getPrimaryKey().getColumns()));
					}
					var defaultMatcher = Pattern.compile("DEFAULT (\\(now\\(\\)\\)|'[^']*'|\\d+)")
							.matcher(definition);
					if (defaultMatcher.find()) {
						assertEquals(defaultMatcher.group(1), column.getDefaultValue(),
								tableName + "." + columnName + " default");
					}
				}
				assertEquals(columnCount, columns.size(), tableName + " columns");
			}
			assertEquals(7, tableCount);
			assertEquals(7, tables.size());

			Map<String, String> expectedIndexes = new TreeMap<>();
			var indexMatcher = INDEX_PATTERN.matcher(sql);
			while (indexMatcher.find()) {
				String key = indexMatcher.group(3) + "." + indexMatcher.group(2);
				String columns = indexMatcher.group(4).replace("`", "").replace(" ", "");
				expectedIndexes.put(key, (indexMatcher.group(1) != null ? "unique:" : "index:") + columns);
			}
			Map<String, String> actualIndexes = new TreeMap<>();
			tables.forEach((name, table) -> {
				table.getIndexes().forEach((indexName, index) -> actualIndexes.put(name + "." + indexName,
						(index.isUnique() ? "unique:" : "index:") + String.join(",", columnNames(index.getSelectables().stream().map(Column.class::cast).toList()))));
				table.getUniqueKeys().forEach((keyName, key) -> actualIndexes.put(name + "." + keyName,
						"unique:" + String.join(",", columnNames(key.getColumns()))));
			});
			assertEquals(expectedIndexes, actualIndexes);

			Set<String> expectedForeignKeys = new TreeSet<>();
			var foreignKeyMatcher = FOREIGN_KEY_PATTERN.matcher(sql);
			while (foreignKeyMatcher.find()) {
				expectedForeignKeys.add(foreignKeyMatcher.group(1) + "." + foreignKeyMatcher.group(2)
						+ "->" + foreignKeyMatcher.group(3) + "." + foreignKeyMatcher.group(4));
			}
			Set<String> actualForeignKeys = new TreeSet<>();
			tables.forEach((name, table) -> table.getForeignKeyCollection().forEach(key ->
					actualForeignKeys.add(name + "." + String.join(",", columnNames(key.getColumns()))
							+ "->" + key.getReferencedTable().getName() + "."
							+ String.join(",", columnNames(key.getReferencedTable().getPrimaryKey().getColumns())))));
			assertEquals(expectedForeignKeys, actualForeignKeys);
			assertEquals(7, actualForeignKeys.size());
		}
	}

	private static List<String> columnNames(List<Column> columns) {
		return columns.stream().map(Column::getName).toList();
	}

	private static String normalizeType(String type) {
		return type.toLowerCase().replace("integer", "int");
	}
}
