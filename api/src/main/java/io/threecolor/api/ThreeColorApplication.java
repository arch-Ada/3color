package io.threecolor.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.context.annotation.Import(io.threecolor.supply.SupplyConfiguration.class)
@SpringBootApplication
public class ThreeColorApplication {
  @org.springframework.context.annotation.Bean
  io.threecolor.generation.PuzzleBank bundledBank() {
    return io.threecolor.generation.PuzzleBank.bundled();
  }

  public static void main(String[] args) {
    String role = System.getenv().getOrDefault("THREECOLOR_ROLE", "web");
    if (role.equals("worker"))
      new org.springframework.boot.builder.SpringApplicationBuilder(
              io.threecolor.worker.WorkerApplication.class)
          .web(org.springframework.boot.WebApplicationType.NONE)
          .run(args);
    else if (role.equals("web")) SpringApplication.run(ThreeColorApplication.class, args);
    else throw new IllegalArgumentException("THREECOLOR_ROLE must be web or worker");
  }
}
