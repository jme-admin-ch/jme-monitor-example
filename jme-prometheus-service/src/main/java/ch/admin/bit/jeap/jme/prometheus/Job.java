package ch.admin.bit.jeap.jme.prometheus;

import lombok.Value;
import org.springframework.util.StringUtils;

@Value
public class Job {

    private static final int MAX_ALLOWED_JOB_DESCRIPTION_SIZE = 20;

    String id;
    JobPriority priority;
    String description;

    public static long getMaxAllowedJobDescriptionSize() {
        return MAX_ALLOWED_JOB_DESCRIPTION_SIZE;
    }

    public boolean checkValid() {
        return StringUtils.hasText(description) &&
                (description.length() <= getMaxAllowedJobDescriptionSize()) &&
                !description.contains("invalid");
    }

}
