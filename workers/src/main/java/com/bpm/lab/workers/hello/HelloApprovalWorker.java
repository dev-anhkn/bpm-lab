package com.bpm.lab.workers.hello;

import com.bpm.lab.workers.logging.JobMdc;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.exception.CamundaError;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Các worker cho quy trình processes/00-hello-approval. */
@Slf4j
@Component
public class HelloApprovalWorker {

  /** Hạn mức tối đa giả lập; vượt mức này thì core banking từ chối. */
  private static final long MAX_LIMIT = 1_000_000_000L;

  /** Thời gian chờ trước khi engine cho retry job bị lỗi kỹ thuật. */
  private static final Duration RETRY_BACKOFF = Duration.ofSeconds(10);

  // Trả về Map thì starter tự complete job và ghi các biến này vào process.
  @JobWorker(type = "hello-approval.apply-limit")
  public Map<String, Object> applyLimit(
      ActivatedJob job,
      @Variable String customerId,
      @Variable Long requestedLimit,
      @Variable Boolean simulateFailure) {

    // Gắn pi/jobKey vào log, tự dọn khi ra khỏi khối try (kể cả khi có exception).
    try (var _ = JobMdc.of(job)) {
      log.info(
          "hello-approval.apply-limit customer={} limit={} retriesLeft={}",
          customerId,
          requestedLimit,
          job.getRetries());
      return doApplyLimit(job, requestedLimit, simulateFailure);
    }
  }

  private Map<String, Object> doApplyLimit(
      ActivatedJob job, Long requestedLimit, Boolean simulateFailure) {

    // Lỗi kỹ thuật giả lập: fail job, giảm retries và chờ backoff; hết retries thì thành incident.
    if (Boolean.TRUE.equals(simulateFailure)) {
      throw CamundaError.jobError(
          "Core banking timeout (giả lập)", null, job.getRetries() - 1, RETRY_BACKOFF);
    }

    // Lỗi nghiệp vụ: ném BPMN error để đi nhánh error boundary LIMIT_EXCEEDED, không retry.
    if (requestedLimit != null && requestedLimit > MAX_LIMIT) {
      throw CamundaError.bpmnError(
          "LIMIT_EXCEEDED", "Hạn mức " + requestedLimit + " vượt ngưỡng " + MAX_LIMIT);
    }

    // Giả lập gọi core thành công, trả về thời điểm áp dụng.
    return Map.of("appliedAt", Instant.now().toString());
  }

  // Timer lặp mỗi 2 phút nhắc MG duyệt; task duyệt vẫn giữ nguyên, hàm void nên chỉ complete job.
  @JobWorker(type = "hello-approval.remind")
  public void remind(
      ActivatedJob job, @Variable String customerId, @Variable Long requestedLimit) {
    try (var _ = JobMdc.of(job)) {
      log.info(
          "hello-approval.remind: nhắc MG duyệt customer={} limit={}", customerId, requestedLimit);
    }
  }

  // Sau 3 phút MG chưa duyệt thì báo cấp trên (thực tế là gửi mail/chat), task duyệt vẫn giữ.
  @JobWorker(type = "hello-approval.escalate")
  public void escalate(
      ActivatedJob job, @Variable String customerId, @Variable Long requestedLimit) {
    try (var _ = JobMdc.of(job)) {
      log.warn(
          "hello-approval.escalate: MG chưa duyệt sau 3', báo cấp trên customer={} limit={}",
          customerId,
          requestedLimit);
    }
  }
}
