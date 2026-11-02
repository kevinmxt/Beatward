package dev.beatward.monitor.target;

import java.net.URI;

public interface TargetPolicy {

    void validate(URI target);
}
