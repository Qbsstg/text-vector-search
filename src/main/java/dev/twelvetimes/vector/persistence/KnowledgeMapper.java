package dev.twelvetimes.vector.persistence;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 所有外部值使用绑定参数；更新只触及当前职责拥有的字段。 */
@Mapper
public interface KnowledgeMapper {
    String COLUMNS = """
            d.document_id, d.raw_text, d.channel, d.submitted_at, d.invalidated, d.invalidated_at,
            OCTET_LENGTH(d.vector_bytes), d.vector_completed_at, d.hit_count,
            t.task_id, t.status, t.embedding_version, t.run_token, t.attempt_count,
            t.started_at, t.finished_at, t.error_code, t.error_message
            """;
    String JOIN = " FROM documents d JOIN embedding_tasks t ON d.document_id=t.document_id ";

    @Select("SELECT " + COLUMNS + JOIN + " WHERE d.document_id=#{id}")
    DocumentRow findDocument(String id);

    @Select("SELECT " + COLUMNS + JOIN + " WHERE t.task_id=#{id}")
    DocumentRow findTask(String id);

    @Select("SELECT COUNT(*) FROM documents")
    long countDocuments();

    @Select("SELECT COUNT(*) FROM embedding_tasks WHERE status IN ('QUEUED','RUNNING')")
    long countPending();

    @Select("SELECT COUNT(*) FROM embedding_tasks WHERE status='RUNNING'")
    long countRunning();

    @Insert("""
            INSERT INTO documents(document_id,raw_text,channel,submitted_at)
            VALUES (#{id},#{text},#{channel},#{time})
            """)
    int insertDocument(@Param("id") String id, @Param("text") String text,
                       @Param("channel") String channel, @Param("time") Instant time);

    @Insert("""
            INSERT INTO embedding_tasks(task_id,document_id,status,embedding_version,submitted_at)
            VALUES (#{taskId},#{documentId},'QUEUED',#{version},#{time})
            """)
    int insertTask(@Param("taskId") String taskId, @Param("documentId") String documentId,
                   @Param("version") String version, @Param("time") Instant time);

    @Select("""
            <script>SELECT
            """ + COLUMNS + JOIN + """
            WHERE 1=1
            <if test='channel != null'>AND d.channel=#{channel}</if>
            <if test='invalidated != null'>AND d.invalidated=#{invalidated}</if>
            ORDER BY d.submitted_at DESC,d.document_id ASC LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<DocumentRow> list(@Param("channel") String channel, @Param("invalidated") Boolean invalidated,
                           @Param("size") int size, @Param("offset") long offset);

    @Select("""
            <script>SELECT COUNT(*) FROM documents WHERE 1=1
            <if test='channel != null'>AND channel=#{channel}</if>
            <if test='invalidated != null'>AND invalidated=#{invalidated}</if>
            </script>
            """)
    long countFiltered(@Param("channel") String channel, @Param("invalidated") Boolean invalidated);

    @Update("""
            UPDATE documents SET invalidated=TRUE,invalidated_at=#{time}
            WHERE document_id=#{id} AND invalidated=FALSE
            """)
    int invalidate(@Param("id") String id, @Param("time") Instant time);

    @Select("SELECT task_id FROM embedding_tasks WHERE status='QUEUED' ORDER BY submitted_at,task_id LIMIT #{limit}")
    List<String> queued(int limit);

    @Update("""
            UPDATE embedding_tasks SET status='RUNNING',run_token=#{token},attempt_count=attempt_count+1,
            started_at=#{time} WHERE task_id=#{id} AND status='QUEUED'
            """)
    int claim(@Param("id") String id, @Param("token") String token, @Param("time") Instant time);
}
