package com.bpm.lab.workers.smoke;

import com.bpm.lab.workers.logging.JobMdc;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Worker cho service task "Chào hỏi" trong processes/00-smoke-test. */
@Slf4j
@Component
public class SayHelloWorker {

  // Tạo lời chào theo tên (trống thì gọi "bạn"), trả về Map để starter tự complete job.
  @JobWorker(type = "smoke.say-hello")
  public Map<String, Object> sayHello(ActivatedJob job, @Variable("name") String name) {
    try (var _ = JobMdc.of(job)) {
      String greeting = "Xin chào " + (name == null || name.isBlank() ? "bạn" : name);
      log.info("smoke.say-hello -> {}", greeting);
      return Map.of("greeting", greeting);
    }
  }
}
