package dev.twelvetimes.vector.persistence;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 仅启动且旧进程已退出时调用，不用超时猜测在线Worker是否死亡。 */
@Mapper
public interface TaskRecoveryMapper {
    @Select("SELECT task_id,attempt_count,embedding_version,submitted_at FROM embedding_tasks WHERE status='RUNNING' ORDER BY task_id")
    List<RecoveryTask> running();

    @Update("""
            UPDATE embedding_tasks SET status='QUEUED',run_token=NULL,started_at=NULL,finished_at=NULL,
            error_code=NULL,error_message=NULL WHERE status='RUNNING'
            """)
    int requeueRunning();
}
