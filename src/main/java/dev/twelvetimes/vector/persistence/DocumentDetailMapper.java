package dev.twelvetimes.vector.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** 仅文档详情加载向量，列表与任务轮询继续使用不含向量字节的原查询。 */
@Mapper
public interface DocumentDetailMapper {
    /** 元数据、任务状态和向量由同一条 SQL 读取，避免终态提交前后拼接出混合快照。 */
    @Select("""
            SELECT d.document_id, d.raw_text, d.channel, d.submitted_at, d.invalidated,
                   d.invalidated_at, d.vector_completed_at, d.hit_count, t.task_id, t.status,
                   d.embedding_version, d.vector_bytes
            FROM documents d JOIN embedding_tasks t ON d.document_id=t.document_id
            WHERE d.document_id=#{id}
            """)
    DocumentDetailRow findDetail(String id);
}
