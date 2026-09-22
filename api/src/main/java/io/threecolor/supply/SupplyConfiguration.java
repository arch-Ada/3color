package io.threecolor.supply;

import com.zaxxer.hikari.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "threecolor.supply.enabled", havingValue = "true")
public class SupplyConfiguration {
  @Bean(destroyMethod = "close")
  HikariDataSource supplyDataSource(Environment env) {
    var config = new HikariConfig();
    String url = env.getRequiredProperty("threecolor.supply.jdbc-url");
    if (!url.startsWith("jdbc:postgresql://"))
      throw new IllegalArgumentException("Use a PostgreSQL JDBC URL");
    config.setJdbcUrl(url);
    config.setUsername(env.getRequiredProperty("threecolor.supply.username"));
    config.setPassword(env.getRequiredProperty("threecolor.supply.password"));
    config.setMaximumPoolSize(4);
    config.setMinimumIdle(0);
    config.setConnectionTimeout(5000);
    config.setValidationTimeout(2000);
    config.setPoolName("puzzle-supply");
    config.addDataSourceProperty("connectTimeout", "5");
    config.addDataSourceProperty("socketTimeout", "30");
    config.addDataSourceProperty("tcpKeepAlive", "true");
    var ds = new HikariDataSource(config);
    try {
      Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
      return ds;
    } catch (RuntimeException e) {
      ds.close();
      throw e;
    }
  }

  @Bean
  org.springframework.boot.ApplicationRunner importSupply(PuzzleStore store) {
    return args -> {
      int added =
          store.importBank(
              io.threecolor.generation.PuzzleBank.bundled(), SupplyIdentity.bankHash());
      org.slf4j.LoggerFactory.getLogger(SupplyConfiguration.class)
          .info("Puzzle supply ready; imported {} bundled puzzles", added);
    };
  }

  @Bean
  PuzzleStore puzzleStore(DataSource ds) {
    return new PuzzleStore(ds);
  }

  @Bean
  DiscoveryQueue discoveryQueue(DataSource ds, PuzzleStore store) {
    return new DiscoveryQueue(ds, store);
  }
}
