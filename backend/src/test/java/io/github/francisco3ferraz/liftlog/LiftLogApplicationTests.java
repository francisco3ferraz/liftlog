package io.github.francisco3ferraz.liftlog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LiftLogApplicationTests {

    @Value("${spring.application.name}")
    String applicationName;

    @Value("${server.shutdown}")
    String shutdown;

    @Value("${spring.threads.virtual.enabled}")
    boolean virtualThreads;

    @Value("${spring.jpa.hibernate.ddl-auto}")
    String ddlAuto;

    @Value("${spring.jpa.open-in-view}")
    boolean openInView;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void contextLoadsWithBaseConfiguration() {
        assertThat(applicationName).isEqualTo("liftlog");
        assertThat(shutdown).isEqualTo("graceful");
        assertThat(virtualThreads).isTrue();
    }

    @Test
    void jpaValidatesSchemaAndKeepsSessionsOutOfViews() {
        assertThat(ddlAuto).isEqualTo("validate");
        assertThat(openInView).isFalse();
    }

    @Test
    void flywayBaselineEnablesCitext() {
        assertThat(jdbc.queryForObject(
                        "select count(*) from flyway_schema_history where version = '1' and success", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select 'Bench'::citext = 'bench'::citext", Boolean.class))
                .isTrue();
    }
}
