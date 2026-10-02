package com.example.smarttask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.SpringApplication;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.smarttask.repository.NotificationRepository;
import com.example.smarttask.repository.TaskRepository;

@EnabledIfEnvironmentVariable(named = "SMARTTASK_MYSQL_TEST_URL", matches = ".+")
class MySqlMigrationTests {

	@Test
	void migratesAnIsolatedRealMysqlDatabaseAndEnforcesForeignKeys() throws Exception {
		var schema = "smarttask_it_" + UUID.randomUUID().toString().replace("-", "");
		var username = System.getenv().getOrDefault("SMARTTASK_MYSQL_TEST_USERNAME", "root");
		var password = System.getenv().getOrDefault("SMARTTASK_MYSQL_TEST_PASSWORD", "");
		var serverUrl = System.getenv("SMARTTASK_MYSQL_TEST_URL");
		var queryIndex = serverUrl.indexOf('?');
		var baseUrl = queryIndex < 0 ? serverUrl : serverUrl.substring(0, queryIndex);
		assertThat(baseUrl).endsWith("/");
		var databaseUrl = baseUrl + schema + (queryIndex < 0 ? "" : serverUrl.substring(queryIndex));
		try (var connection = DriverManager.getConnection(
				serverUrl, username, password);
				var statement = connection.createStatement()) {
			// Only this randomly named test database may be created or dropped.
			assertThat(schema).matches("smarttask_it_[a-f0-9]{32}");
			statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
			try {
				var flyway = Flyway.configure()
						.dataSource(databaseUrl, username, password)
						.defaultSchema(schema)
						.locations("classpath:db/migration/mysql")
						.load();
				assertThat(flyway.migrate().migrationsExecuted).isEqualTo(10);
				assertThat(flyway.migrate().migrationsExecuted).isZero();
				try (var database = DriverManager.getConnection(databaseUrl, username, password);
						var fixture = database.createStatement()) {
				fixture.executeUpdate("""
						INSERT INTO users (email, password_hash, full_name, google_subject)
						VALUES ('mysql-test@example.com', '{noop}test-only', 'Kiểm thử MySQL', 'mysql-test-subject')
						""");
				fixture.executeUpdate("INSERT INTO projects (created_by_id, name) VALUES (1, 'Dự án thử')");
				fixture.executeUpdate("INSERT INTO project_members (project_id, user_id, project_role) VALUES (1, 1, 'OWNER')");
				fixture.executeUpdate("""
						INSERT INTO task_statuses (project_id, code, display_name, display_order, is_terminal, is_active)
						VALUES (1, 'TODO', 'Cần làm', 1, false, true)
						""");
				fixture.executeUpdate("""
						INSERT INTO tasks (project_id, created_by_id, assignee_id, status_id, title, due_at)
						SELECT 1, 1, 1, id, 'Task thử', '2030-01-01 12:00:00'
						FROM task_statuses WHERE project_id = 1 AND code = 'TODO'
						""");
				try (var rows = fixture.executeQuery("SELECT title FROM tasks WHERE project_id = 1")) {
					assertThat(rows.next()).isTrue();
					assertThat(rows.getString(1)).isEqualTo("Task thử");
				}
				assertThatThrownBy(() -> fixture.executeUpdate("UPDATE tasks SET assignee_id = 999 WHERE id = 1"))
						.isInstanceOf(java.sql.SQLIntegrityConstraintViolationException.class);
				assertThatThrownBy(() -> fixture.executeUpdate("UPDATE tasks SET parent_task_id = id WHERE id = 1"))
						.isInstanceOf(java.sql.SQLException.class)
						.hasMessageContaining("own parent");
				}
				verifyApplicationAndDeadlineQueries(databaseUrl, username, password);
			} finally {
				statement.execute("DROP DATABASE `" + schema + "`");
			}
		}
	}

	private void verifyApplicationAndDeadlineQueries(String databaseUrl, String username, String password) {
		try (var application = SpringApplication.run(SmartTaskApplication.class,
				"--server.port=0", "--spring.config.import=",
				"--spring.datasource.url=" + databaseUrl,
				"--spring.datasource.username=" + username,
				"--spring.datasource.password=" + password,
				"--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
				"--spring.flyway.locations=classpath:db/migration/mysql",
				"--smarttask.oauth.google.enabled=false",
				"--smarttask.ai.provider=DEMO",
				"--smarttask.bootstrap-admin.email=",
				"--smarttask.bootstrap-admin.password=")) {
			var transactions = new TransactionTemplate(application.getBean(PlatformTransactionManager.class));
			var notifications = application.getBean(NotificationRepository.class);
			var tasks = application.getBean(TaskRepository.class);
			transactions.executeWithoutResult(ignored -> {
				assertThat(tasks.findActiveTask(1L, 1L).orElseThrow().getTitle()).isEqualTo("Task thử");
				var from = LocalDateTime.of(2030, 1, 1, 0, 0);
				var until = from.plusDays(3);
				assertThat(notifications.insertDeadlineNotificationsForAllAssignees(
						"TASK_DUE_SOON", "Sắp hạn", "Task: ", "", from, until)).isEqualTo(1);
				assertThat(notifications.insertDeadlineNotificationsForAllAssignees(
						"TASK_DUE_SOON", "Sắp hạn", "Task: ", "", from, until)).isZero();
				var task = tasks.findById(1L).orElseThrow();
				task.setDueAt(task.getDueAt().plusDays(1));
				tasks.saveAndFlush(task);
				assertThat(notifications.insertDeadlineNotifications(1L,
						"TASK_DUE_SOON", "Sắp hạn", "Task: ", "", from, until)).isEqualTo(1);
				assertThat(notifications.count()).isEqualTo(2);
			});
		}
	}
}
