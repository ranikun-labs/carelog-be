package carelog.carelog.common.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.Gauge;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalLong;
import java.util.stream.Stream;

/** Linux procfs의 프로세스 RSS를 노출하며 procfs가 없는 환경에서는 Gauge를 만들지 않는다. */
@Component
public final class LinuxProcessResidentMemoryMetrics implements MeterBinder, AopInfrastructureBean {

    private static final Path PROCESS_STATUS = Path.of("/proc/self/status");

    @Override
    public void bindTo(MeterRegistry registry) {
        if (!Files.isReadable(PROCESS_STATUS)) return;
        Gauge.builder("process.memory.rss", this, LinuxProcessResidentMemoryMetrics::readResidentBytes)
                .description("Resident set size of the Carelog process read from Linux procfs")
                .baseUnit("bytes")
                .register(registry);
    }

    private double readResidentBytes() {
        try (Stream<String> lines = Files.lines(PROCESS_STATUS)) {
            OptionalLong rssKilobytes = lines
                    .filter(line -> line.startsWith("VmRSS:"))
                    .mapToLong(line -> parseKilobytes(line))
                    .findFirst();
            return rssKilobytes.isPresent() ? rssKilobytes.getAsLong() * 1024.0 : Double.NaN;
        } catch (IOException | RuntimeException ignored) {
            return Double.NaN;
        }
    }

    private static long parseKilobytes(String line) {
        String[] fields = line.trim().split("\\s+");
        return fields.length >= 2 ? Long.parseLong(fields[1]) : -1;
    }
}
