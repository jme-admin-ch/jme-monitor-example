package ch.admin.bit.jeap.jme.prometheus;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class ExampleInfoContributor implements InfoContributor {
    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("exampleWithChild", Collections.singletonMap("key1", "value1"));
        builder.withDetail("exampleWithoutChild", "value");
    }
}
