package com.example.smarttask;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class FlywayMigrationTests {

	@Test
	void baselinesAnExistingSchemaAndAppliesLaterMigrations() throws Exception {
		var url = "jdbc:h2:mem:smart_task_upgrade;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
		try (var keeperConnection = DriverManager.getConnection(url, "sa", "");
				var statement = keeperConnection.createStatement()) {
			statement.execute("create table users (id bigint primary key)");
			statement.execute("create table projects (id bigint primary key)");
			statement.execute("""
					create table task_statuses (
						id smallint not null primary key,
						code varchar(32) not null,
						display_name varchar(100) not null,
						display_order smallint not null,
						is_terminal boolean not null,
						is_active boolean not null,
						constraint uq_task_statuses_code unique (code),
						constraint uq_task_statuses_display_order unique (display_order)
					)
					""");
			statement.execute("insert into task_statuses values (1, 'TODO', 'Cần làm', 1, false, true)");
			statement.execute("insert into task_statuses values (2, 'IN_PROGRESS', 'Đang làm', 2, false, true)");
			statement.execute("insert into task_statuses values (3, 'DONE', 'Hoàn thành', 3, true, true)");
			statement.execute("""
					create table project_members (
						project_id bigint not null,
						user_id bigint not null,
						project_role varchar(16) not null,
						constraint pk_project_members primary key (project_id, user_id),
						constraint ck_project_members_role check (
							project_role in ('OWNER', 'MEMBER')
						)
					)
					""");
			statement.execute("""
					create table tasks (
						id bigint primary key,
						project_id bigint not null,
						status_id smallint not null default 1,
						constraint uq_tasks_project_id_id unique (project_id, id)
						,constraint fk_tasks_status foreign key (status_id) references task_statuses (id)
					)
					""");
			statement.execute("""
					create table notifications (
						id bigint auto_increment primary key,
						recipient_id bigint not null,
						project_id bigint not null,
						task_id bigint null,
						type varchar(32) not null,
						title varchar(160) not null,
						message varchar(500) not null,
						read_at timestamp null,
						created_at timestamp not null default current_timestamp,
						constraint ck_notifications_type check (
							type in ('PROJECT_MEMBER_ADDED', 'TASK_ASSIGNED')
						)
					)
					""");

			var result = Flyway.configure()
					.dataSource(new SingleConnectionDataSource(keeperConnection, true))
					.locations("classpath:db/migration/common", "classpath:db/migration/h2")
					.baselineOnMigrate(true)
					.baselineVersion("1")
					.load()
					.migrate();

			assertThat(result.migrationsExecuted).isEqualTo(9);
			statement.executeUpdate("""
					insert into notifications (
						recipient_id, project_id, task_id, type, title, message, dedupe_key
					) values (1, 1, 1, 'TASK_DUE_SOON', 'Sắp hạn', 'Kiểm thử', 'TASK_DUE_SOON:1')
					""");
			try (var rows = statement.executeQuery(
					"select count(*) from notifications where dedupe_key = 'TASK_DUE_SOON:1'")) {
				assertThat(rows.next()).isTrue();
				assertThat(rows.getInt(1)).isEqualTo(1);
			}

			statement.executeUpdate("insert into users (id) values (1)");
			statement.executeUpdate("update users set google_subject = 'google-subject-1' where id = 1");
			statement.executeUpdate("""
					insert into account_tokens (
						user_id, purpose, token_hash, issued_at, expires_at, credentials_version
					) values (1, 'EMAIL_VERIFICATION', repeat('a', 64), current_timestamp,
						dateadd('MINUTE', 30, current_timestamp), 0)
					""");
			try (var rows = statement.executeQuery(
					"select email_verified, credentials_version from users where id = 1")) {
				assertThat(rows.next()).isTrue();
				assertThat(rows.getBoolean(1)).isFalse();
				assertThat(rows.getLong(2)).isZero();
			}
			statement.executeUpdate("insert into projects (id) values (1)");
			statement.executeUpdate("insert into tasks (id, project_id, status_id) values (1, 1, 1)");
			statement.executeUpdate("""
					insert into project_members (project_id, user_id, project_role)
					values (1, 1, 'VIEWER')
					""");
			statement.executeUpdate("""
					insert into task_comments (project_id, task_id, author_id, content)
					values (1, 1, 1, 'Bình luận sau nâng cấp')
					""");
			try (var rows = statement.executeQuery("select count(*) from task_comments")) {
				assertThat(rows.next()).isTrue();
				assertThat(rows.getInt(1)).isEqualTo(1);
			}

			statement.executeUpdate("""
					insert into task_attachments (
						project_id, task_id, uploaded_by_id, original_name,
						stored_name, content_type, size_bytes
					) values (1, 1, 1, 'ke-hoach.pdf', 'test-file.pdf', 'application/pdf', 12)
					""");
			try (var rows = statement.executeQuery("select count(*) from task_attachments")) {
				assertThat(rows.next()).isTrue();
				assertThat(rows.getInt(1)).isEqualTo(1);
			}
		}
	}
}
