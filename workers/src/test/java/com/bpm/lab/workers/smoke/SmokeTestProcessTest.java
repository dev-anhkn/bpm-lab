package com.bpm.lab.workers.smoke;

import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;
import static io.camunda.process.test.api.assertions.ElementSelectors.byId;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test tích hợp: engine thật (Testcontainers) + worker thật.
 * Test ở mức model (mock worker) sẽ dùng processTestContext.mockJobWorker(...).
 */
@Disabled("Tạm tắt: chưa chốt cách deploy model nên engine test không có quy trình smoke-test")
@SpringBootTest
@CamundaSpringProcessTest
@ActiveProfiles("test")
class SmokeTestProcessTest {

  @Autowired private CamundaClient client;

  @Test
  void shouldGreetAndComplete() {
    var instance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("smoke-test")
            .latestVersion()
            .variable("name", "Anh")
            .send()
            .join();

    assertThatProcessInstance(instance)
        .hasCompletedElements(byId("Task_say_hello"))
        .hasVariable("greeting", "Xin chào Anh")
        .isCompleted();
  }
}
