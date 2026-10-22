package dev.twelvetimes.vector.unit;

import dev.twelvetimes.b2.B2CompositionTestApplication;
import dev.twelvetimes.vector.config.VectorProperties;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/** 每个用例只使用自己的绝对临时文件库，无测试外层事务。 */
final class B2TestSupport {
    private B2TestSupport() { }

    static ConfigurableApplicationContext start(Path directory, String... overrides) {
        var args = new ArrayList<>(List.of("--spring.main.web-application-type=none",
                "--spring.datasource.url=jdbc:h2:file:" + directory.toAbsolutePath() + "/knowledge;WRITE_DELAY=0",
                "--logging.level.root=ERROR", "--spring.main.banner-mode=off"));
        args.addAll(List.of(overrides));
        return new SpringApplication(B2CompositionTestApplication.class).run(args.toArray(String[]::new));
    }

    static VectorProperties properties(int maximumText) {
        return new VectorProperties(1, 1, 10, 20, 1, maximumText, 262144,
                100, 100, 1, 500, 32, 2);
    }
}
