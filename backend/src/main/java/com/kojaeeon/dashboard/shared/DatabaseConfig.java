package com.kojaeeon.dashboard.shared;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

@Configuration
public class DatabaseConfig {

  @Bean
  DataSource dataSource(@Value("${dashboard.db-path}") String file) throws Exception {
    Path path = Path.of(file).toAbsolutePath().normalize();
    Files.createDirectories(path.getParent());
    var source = new DriverManagerDataSource(
      "jdbc:sqlite:" + path + "?busy_timeout=10000&foreign_keys=on"
    );
    source.setDriverClassName("org.sqlite.JDBC");
    new ResourceDatabasePopulator(
      new ClassPathResource("db/migration/V001__dashboard.sql")
    ).execute(source);
    return source;
  }
}
