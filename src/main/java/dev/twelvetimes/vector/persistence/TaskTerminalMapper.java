package dev.twelvetimes.vector.persistence;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 先锁住本次RUNNING令牌，再在同一事务写文档，绝不覆盖失效或计数。 */
@Mapper
public interface TaskTerminalMapper {
    @Update("""
            UPDATE embedding_tasks SET status='SUCCEEDED',finished_at=#{time},error_code=NULL,error_message=NULL
            WHERE task_id=#{taskId} AND document_id=#{documentId} AND status='RUNNING'
            AND run_token=#{token} AND embedding_version=#{version}
            """)
    int succeed(@Param("taskId") String taskId, @Param("documentId") String documentId,
                @Param("token") String token, @Param("version") String version, @Param("time") Instant time);

    @Update("""
            UPDATE documents SET vector_bytes=#{bytes},embedding_version=#{version},vector_completed_at=#{time}
            WHERE document_id=#{documentId}
            """)
    int writeVector(@Param("documentId") String documentId, @Param("bytes") byte[] bytes,
                    @Param("version") String version, @Param("time") Instant time);

    @Update("""
            UPDATE embedding_tasks SET status='FAILED',finished_at=#{time},error_code=#{code},error_message=#{message}
            WHERE task_id=#{taskId} AND document_id=#{documentId} AND status='RUNNING'
            AND run_token=#{token} AND embedding_version=#{version}
            """)
    int fail(@Param("taskId") String taskId, @Param("documentId") String documentId,
             @Param("token") String token, @Param("version") String version, @Param("time") Instant time,
             @Param("code") String code, @Param("message") String message);

    @Select("""
            SELECT t.task_id,t.document_id,t.status,t.run_token,t.embedding_version,t.finished_at,
            t.error_code,t.error_message,d.vector_bytes,d.embedding_version,d.vector_completed_at
            FROM embedding_tasks t JOIN documents d ON d.document_id=t.document_id WHERE t.task_id=#{id}
            """)
    TerminalSnapshot snapshot(String id);
}
