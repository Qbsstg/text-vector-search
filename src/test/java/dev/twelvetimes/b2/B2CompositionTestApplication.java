package dev.twelvetimes.b2;

import dev.twelvetimes.vector.VectorSearchApplication;
import dev.twelvetimes.vector.config.B3TaskConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/** 保留B2阶段无生产处理器/不恢复断言；只排除B3组成，不改变原控制器、SQL或断言。 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ConfigurationPropertiesScan("dev.twelvetimes.vector.config")
@MapperScan("dev.twelvetimes.vector.persistence")
@ComponentScan(basePackages = "dev.twelvetimes.vector", excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE, classes = {VectorSearchApplication.class, B3TaskConfiguration.class}))
public class B2CompositionTestApplication {
}
