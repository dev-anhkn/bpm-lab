package com.bpm.lab.workers.logging;

import io.camunda.client.api.response.ActivatedJob;
import org.slf4j.MDC;

/** Gắn pi/jobKey vào MDC khi xử lý job; dùng try-with-resources để thread sau không dính log cũ. */
public final class JobMdc implements AutoCloseable {

  public static final String PROCESS_INSTANCE_KEY = "pi";
  public static final String JOB_KEY = "jobKey";

  private JobMdc() {}

  // Đặt pi/jobKey của job hiện tại vào MDC để mọi dòng log trong khối try đều có.
  public static JobMdc of(ActivatedJob job) {
    MDC.put(PROCESS_INSTANCE_KEY, String.valueOf(job.getProcessInstanceKey()));
    MDC.put(JOB_KEY, String.valueOf(job.getKey()));
    return new JobMdc();
  }

  // Chỉ xóa đúng key mình đặt, không dùng MDC.clear() để khỏi xóa key khác như traceId.
  @Override
  public void close() {
    MDC.remove(PROCESS_INSTANCE_KEY);
    MDC.remove(JOB_KEY);
  }
}
