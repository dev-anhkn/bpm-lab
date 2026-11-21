package com.bpm.lab.workers.logging;

import io.camunda.client.api.response.ActivatedJob;
import org.slf4j.MDC;

/**
 * Gắn {@code pi} (processInstanceKey) và {@code jobKey} vào MDC trong lúc xử lý một job.
 *
 * <p>MDC là ThreadLocal. Worker chạy trên thread pool, thread xử lý xong job A sẽ được dùng lại cho
 * job B → nếu không xóa, log của B mang nhầm pi của A. Vì vậy luôn dùng với try-with-resources:
 *
 * <pre>{@code
 * try (var _ = JobMdc.of(job)) {
 *   log.info("..."); // dòng log có pi=... jobKey=...
 * }
 * }</pre>
 *
 * <p>close() chỉ xóa đúng các key mình đặt, không gọi MDC.clear(): MDC có thể đang giữ key của thứ
 * khác (vd traceId của tracing), clear() sẽ xóa luôn của họ.
 */
public final class JobMdc implements AutoCloseable {

  public static final String PROCESS_INSTANCE_KEY = "pi";
  public static final String JOB_KEY = "jobKey";

  private JobMdc() {}

  public static JobMdc of(ActivatedJob job) {
    MDC.put(PROCESS_INSTANCE_KEY, String.valueOf(job.getProcessInstanceKey()));
    MDC.put(JOB_KEY, String.valueOf(job.getKey()));
    return new JobMdc();
  }

  @Override
  public void close() {
    MDC.remove(PROCESS_INSTANCE_KEY);
    MDC.remove(JOB_KEY);
  }
}
