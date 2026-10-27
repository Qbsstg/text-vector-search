package dev.twelvetimes.vector.unit;

import dev.twelvetimes.vector.api.contract.ApiModels.DocumentDetail;
import dev.twelvetimes.vector.api.contract.ApiModels.VectorInfo;
import dev.twelvetimes.vector.api.contract.TaskStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VectorInfoContractTest {
    @Test
    void 向量快照独立持有数值并保留负零原始位() {
        var values = new ArrayList<>(List.of(-0.0f, Float.MIN_VALUE, Float.MAX_VALUE));
        var info = new VectorInfo("stored-version", values.size(), values.size() * Float.BYTES,
                false, "stored-digest", values);
        values.set(0, 1.0f);
        assertEquals(Float.floatToRawIntBits(-0.0f), Float.floatToRawIntBits(info.values().get(0)));
        assertEquals(Float.MIN_VALUE, info.values().get(1));
        assertEquals(Float.MAX_VALUE, info.values().get(2));
        assertThrows(UnsupportedOperationException.class, () -> info.values().set(0, 2.0f));
    }

    @Test
    void 任务内嵌旧详情构造器继续提供轻量快照() {
        var detail = new DocumentDetail("doc", "default", "task", TaskStatus.SUCCEEDED,
                true, false, Instant.EPOCH, Instant.EPOCH, null, "9007199254740993", "原文");
        assertNull(detail.vectorInfo());
        assertEquals("9007199254740993", detail.hitCount());
        assertEquals("原文", detail.text());
    }
}
