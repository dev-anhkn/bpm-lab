package com.bpm.lab.workers.smoke;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Worker cho service task "Chào hỏi" trong processes/00-smoke-test. */
@Slf4j
@Component
public class SayHelloWorker {

  // autoComplete mặc định = true: return Map -> starter tự gọi complete job với các biến này.
  @JobWorker(type = "smoke.say-hello")
  public Map<String, Object> sayHello(@Variable("name") String name) {
    String greeting = "Xin chào " + (name == null || name.isBlank() ? "bạn" : name);
    log.info("smoke.say-hello -> {}", greeting);
    return Map.of("greeting", greeting);
  }
}
