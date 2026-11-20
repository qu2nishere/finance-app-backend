package com.quan.reportservice.ingest;

/**
 * Event co eventType ma report-service chua biet xu ly.
 * Consumer ghi thang vao dead_letter_events (khong retry, khong danh dau processed_events)
 * de phat lai sau khi deploy ban co xu ly loai nay.
 */
public class UnsupportedEventTypeException extends RuntimeException {

    public static final String CODE = "UNSUPPORTED_EVENT_TYPE";

    public UnsupportedEventTypeException(String eventType) {
        super(CODE + ": " + eventType);
    }
}
