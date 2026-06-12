package com.lawagent.api;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.lawagent.api.mapper")
public class LawAgentApplication {
  public static void main(String[] args) {
    SpringApplication.run(LawAgentApplication.class, args);
  }
}
