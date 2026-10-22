package dev.twelvetimes.vector.persistence;

import dev.twelvetimes.vector.search.SearchCandidate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 资格过滤先于评分；计数不再过滤失效状态，以保留已经读取的候选快照语义。 */
@Mapper
public interface SearchMapper {
    @Select("SELECT COUNT(*) FROM documents")
    long countDocuments();

    @Select("""
            <script>
            SELECT d.document_id,d.channel,d.vector_bytes,SUBSTRING(d.raw_text,1,400)
            FROM documents d JOIN embedding_tasks t ON t.document_id=d.document_id
            WHERE t.status='SUCCEEDED' AND d.invalidated=FALSE
            AND d.vector_bytes IS NOT NULL AND OCTET_LENGTH(d.vector_bytes)=1024
            AND d.embedding_version=#{version} AND t.embedding_version=#{version}
            <if test='channel != null'>AND d.channel=#{channel}</if>
            ORDER BY d.document_id LIMIT #{limit}
            </script>
            """)
    List<SearchCandidate> candidates(@Param("version") String version, @Param("channel") String channel,
                                     @Param("limit") int limit);

    @Update("UPDATE documents SET hit_count=hit_count+1 WHERE document_id=#{documentId}")
    int incrementHit(String documentId);
}
