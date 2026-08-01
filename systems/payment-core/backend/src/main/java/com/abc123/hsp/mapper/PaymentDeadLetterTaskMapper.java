package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.PaymentDeadLetterTaskQueryDTO;
import com.abc123.hsp.entity.PaymentDeadLetterTaskEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** MQ 死信补偿任务 Mapper。 */
public interface PaymentDeadLetterTaskMapper {

    int insertIgnore(PaymentDeadLetterTaskEntity entity);

    List<PaymentDeadLetterTaskEntity> findAll(@Param("query") PaymentDeadLetterTaskQueryDTO query);

    long count(@Param("query") PaymentDeadLetterTaskQueryDTO query);

    PaymentDeadLetterTaskEntity findByTaskNo(@Param("taskNo") String taskNo);

    int markReadyToReplay(@Param("taskNo") String taskNo,
                          @Param("operator") String operator,
                          @Param("resolutionNote") String resolutionNote);

    int markManuallyResolved(@Param("taskNo") String taskNo,
                             @Param("operator") String operator,
                             @Param("resolutionNote") String resolutionNote);

    int markReplaying(@Param("taskNo") String taskNo, @Param("operator") String operator);

    int markReplayed(@Param("taskNo") String taskNo);

    int markReplayFailed(@Param("taskNo") String taskNo, @Param("resolutionNote") String resolutionNote);
}
