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

/** Worker cho service task "Cập nhật hạn mức" trong processes/00-hello-approval. */
@Slf4j
@Component
public class HelloApprovalWorker {

  /** Ngưỡng giả lập: core banking không cho cấp hạn mức vượt mức này. */
  private static final long MAX_LIMIT = 1_000_000_000L;

  /** Thời gian chờ giữa các lần retry khi lỗi kỹ thuật. */
  private static final Duration RETRY_BACKOFF = Duration.ofSeconds(10);

  // autoComplete mặc định = true: return Map -> starter tự complete job + merge biến vào process.
  @JobWorker(type = "hello-approval.apply-limit")
  public Map<String, Object> applyLimit(
      ActivatedJob job,
      @Variable String customerId,
      @Variable Long requestedLimit,
      @Variable Boolean simulateFailure) {

    // pi/jobKey nằm trong MDC → không cần đưa vào message nữa.
    // close() chạy cả khi throw → exception ra khỏi khối try thì MDC đã sạch.
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

    // Bài thử a (lỗi kỹ thuật): start instance với {"simulateFailure": true}.
    // Exception -> starter tự fail job, retries giảm 1; về 0 -> incident trong Operate.
    if (Boolean.TRUE.equals(simulateFailure)) {
      // Fail job có backoff: engine chờ RETRY_BACKOFF rồi mới cho worker lấy lại job.
      // retries phải tự trừ 1 (throw exception thường thì SDK tự trừ, ở đây ta tự quyết).
      throw CamundaError.jobError(
          "Core banking timeout (giả lập)", null, job.getRetries() - 1, RETRY_BACKOFF);
    }

    // Bài thử b (lỗi nghiệp vụ): hạn mức vượt ngưỡng -> BPMN error.
    // Không retry, không incident: engine tìm error boundary event có code khớp và đi nhánh đó.
    if (requestedLimit != null && requestedLimit > MAX_LIMIT) {
      throw CamundaError.bpmnError(
          "LIMIT_EXCEEDED", "Hạn mức " + requestedLimit + " vượt ngưỡng " + MAX_LIMIT);
    }

    // v0: giả lập gọi core.
    return Map.of("appliedAt", Instant.now().toString());
  }

  // v3: timer non-interrupting R/PT2M trên Task_Approve -> "Nhắc duyệt" (lặp mỗi 2 phút).
  // Task MG duyệt vẫn còn; hàm void -> starter tự complete job, không thêm biến.
  @JobWorker(type = "hello-approval.remind")
  public void remind(
      ActivatedJob job, @Variable String customerId, @Variable Long requestedLimit) {
    try (var _ = JobMdc.of(job)) {
      log.info(
          "hello-approval.remind: nhắc MG duyệt customer={} limit={}", customerId, requestedLimit);
    }
  }

  // v3 (cách A): timer non-interrupting PT3M -> "Báo cấp trên". Task MG vẫn còn, chỉ báo động.
  // Thực tế: gửi mail/chat cho cấp trên của người được giao.
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
