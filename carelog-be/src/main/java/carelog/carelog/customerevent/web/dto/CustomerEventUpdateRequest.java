package carelog.carelog.customerevent.web.dto;

import com.fasterxml.jackson.annotation.JsonSetter;

public class CustomerEventUpdateRequest {

    private boolean descriptorPresent;
    private String descriptor;
    private boolean notePresent;
    private String note;
    private boolean scheduledAtPresent;
    private String scheduledAt;
    private boolean occurredAtPresent;
    private String occurredAt;

    @JsonSetter("descriptor")
    public void setDescriptor(String descriptor) {
        this.descriptorPresent = true;
        this.descriptor = descriptor;
    }

    @JsonSetter("note")
    public void setNote(String note) {
        this.notePresent = true;
        this.note = note;
    }

    @JsonSetter("scheduledAt")
    public void setScheduledAt(String scheduledAt) {
        this.scheduledAtPresent = true;
        this.scheduledAt = scheduledAt;
    }

    @JsonSetter("occurredAt")
    public void setOccurredAt(String occurredAt) {
        this.occurredAtPresent = true;
        this.occurredAt = occurredAt;
    }

    public boolean descriptorPresent() {
        return descriptorPresent;
    }

    public String descriptor() {
        return descriptor;
    }

    public boolean notePresent() {
        return notePresent;
    }

    public String note() {
        return note;
    }

    public boolean scheduledAtPresent() {
        return scheduledAtPresent;
    }

    public String scheduledAt() {
        return scheduledAt;
    }

    public boolean occurredAtPresent() {
        return occurredAtPresent;
    }

    public String occurredAt() {
        return occurredAt;
    }
}
