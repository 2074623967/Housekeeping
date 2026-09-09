package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentIssueDutyRosterReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentIssueDutyRosterReviewDTO;
import com.abc123.hsp.dto.PaymentIssueDutyRosterStatusReviewRequestDTO;
import com.abc123.hsp.dto.PaymentIssueDutyRosterUpsertRequestDTO;
import com.abc123.hsp.entity.PaymentIssueDutyRosterEntity;
import com.abc123.hsp.entity.PaymentIssueDutyRosterReviewEntity;
import com.abc123.hsp.mapper.PaymentConfigMapper;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

/** 异常告警值班路由完整候选复核服务测试。 */
@ExtendWith(MockitoExtension.class)
class PaymentIssueDutyRosterConfigServiceImplTest {

    @Mock
    private PaymentConfigMapper paymentConfigMapper;

    @Test
    void shouldSubmitCreateUpdateAndStatusCandidatesWithoutChangingRuntime() {
        when(paymentConfigMapper.findIssueDutyRosterReviews())
                .thenReturn(Collections.singletonList(new PaymentIssueDutyRosterReviewDTO()));
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);

        service.createIssueDutyRoster(rosterRequest("duty_new_p1"));

        PaymentIssueDutyRosterEntity current = roster("ENABLED");
        when(paymentConfigMapper.findIssueDutyRosterByCodeForUpdate("DUTY_CALLBACK_P1"))
                .thenReturn(current);
        PaymentIssueDutyRosterUpsertRequestDTO update = rosterRequest("ignored_code");
        update.setReceiver("支付联合值班");
        service.updateIssueDutyRoster("duty_callback_p1", update);
        service.toggleIssueDutyRoster(statusRequest("duty_callback_p1", false));

        ArgumentCaptor<PaymentIssueDutyRosterReviewEntity> captor =
                ArgumentCaptor.forClass(PaymentIssueDutyRosterReviewEntity.class);
        verify(paymentConfigMapper, org.mockito.Mockito.times(3))
                .insertIssueDutyRosterReview(captor.capture());
        assertEquals("CREATE", captor.getAllValues().get(0).getOperationType());
        assertEquals("DUTY_NEW_P1", captor.getAllValues().get(0).getPendingRosterCode());
        assertEquals(null, captor.getAllValues().get(0).getBaseFingerprint());
        assertEquals("UPDATE", captor.getAllValues().get(1).getOperationType());
        assertEquals("支付联合值班", captor.getAllValues().get(1).getReceiver());
        assertEquals(64, captor.getAllValues().get(1).getBaseFingerprint().length());
        assertEquals("STATUS_CHANGE", captor.getAllValues().get(2).getOperationType());
        assertEquals("DISABLED", captor.getAllValues().get(2).getCandidateStatus());
        verify(paymentConfigMapper, never()).insertIssueDutyRoster(any());
        verify(paymentConfigMapper, never()).updateIssueDutyRoster(any());
    }

    @Test
    void shouldApproveUpdateOnlyWhenCompleteBaselineMatches() {
        PaymentIssueDutyRosterEntity current = roster("ENABLED");
        when(paymentConfigMapper.findIssueDutyRosterByCodeForUpdate("DUTY_CALLBACK_P1"))
                .thenReturn(current);
        when(paymentConfigMapper.findIssueDutyRosterReviews()).thenReturn(Collections.emptyList());
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);
        PaymentIssueDutyRosterUpsertRequestDTO candidate = rosterRequest("DUTY_CALLBACK_P1");
        candidate.setEscalationReceiver("支付技术总监");
        candidate.setEffectiveStartHour(Integer.valueOf(22));
        candidate.setEffectiveEndHour(Integer.valueOf(6));

        service.updateIssueDutyRoster("DUTY_CALLBACK_P1", candidate);

        ArgumentCaptor<PaymentIssueDutyRosterReviewEntity> captor =
                ArgumentCaptor.forClass(PaymentIssueDutyRosterReviewEntity.class);
        verify(paymentConfigMapper).insertIssueDutyRosterReview(captor.capture());
        PaymentIssueDutyRosterReviewEntity review = captor.getValue();
        when(paymentConfigMapper.findIssueDutyRosterReviewForUpdate(review.getRequestNo())).thenReturn(review);
        when(paymentConfigMapper.updateIssueDutyRoster(any())).thenReturn(1);
        when(paymentConfigMapper.completeIssueDutyRosterReview(any(), any(), any(), any(), any()))
                .thenReturn(1);

        service.approveIssueDutyRosterReview(review.getRequestNo(), action("checker-b", "确认调整"));

        verify(paymentConfigMapper).updateIssueDutyRoster(org.mockito.ArgumentMatchers.argThat(
                applied -> "支付技术总监".equals(applied.getEscalationReceiver())
                        && Integer.valueOf(22).equals(applied.getEffectiveStartHour())
                        && Integer.valueOf(6).equals(applied.getEffectiveEndHour())));
        verify(paymentConfigMapper).completeIssueDutyRosterReview(
                review.getRequestNo(), "APPROVED", "success", "checker-b", "确认调整");
    }

    @Test
    void shouldApproveCreateOnlyWhileRosterCodeRemainsUnused() {
        PaymentIssueDutyRosterReviewEntity review = review("CREATE", null);
        review.setRosterCode("DUTY_NEW_P1");
        when(paymentConfigMapper.findIssueDutyRosterReviewForUpdate("DRR-001")).thenReturn(review);
        when(paymentConfigMapper.insertIssueDutyRoster(any())).thenReturn(1);
        when(paymentConfigMapper.completeIssueDutyRosterReview(any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(paymentConfigMapper.findIssueDutyRosterReviews()).thenReturn(Collections.emptyList());

        new PaymentConfigServiceImpl(paymentConfigMapper).approveIssueDutyRosterReview(
                "DRR-001", action("checker-b", "确认新增"));

        verify(paymentConfigMapper).insertIssueDutyRoster(org.mockito.ArgumentMatchers.argThat(
                applied -> "DUTY_NEW_P1".equals(applied.getRosterCode())
                        && "ENABLED".equals(applied.getStatus())));

        when(paymentConfigMapper.findIssueDutyRosterByCodeForUpdate("DUTY_NEW_P1"))
                .thenReturn(roster("ENABLED"));
        IllegalArgumentException occupied = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper).approveIssueDutyRosterReview(
                        "DRR-001", action("checker-b", "再次新增")));
        assertTrue(occupied.getMessage().contains("已存在"));
    }

    @Test
    void shouldRejectSameStatusPendingReviewAndPendingRace() {
        when(paymentConfigMapper.findIssueDutyRosterByCodeForUpdate("DUTY_CALLBACK_P1"))
                .thenReturn(roster("ENABLED"));
        IllegalArgumentException sameStatus = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .toggleIssueDutyRoster(statusRequest("DUTY_CALLBACK_P1", true)));
        assertTrue(sameStatus.getMessage().contains("已启用"));

        when(paymentConfigMapper.findPendingIssueDutyRosterReviewForUpdate("DUTY_CALLBACK_P1"))
                .thenReturn(review("UPDATE", "baseline"));
        IllegalArgumentException pending = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .updateIssueDutyRoster("DUTY_CALLBACK_P1", rosterRequest("DUTY_CALLBACK_P1")));
        assertTrue(pending.getMessage().contains("已有待复核"));

        when(paymentConfigMapper.findPendingIssueDutyRosterReviewForUpdate("DUTY_NEW_P1"))
                .thenReturn(null);
        doThrow(new DuplicateKeyException("uk_issue_duty_roster_review_pending"))
                .when(paymentConfigMapper).insertIssueDutyRosterReview(any());
        IllegalArgumentException race = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .createIssueDutyRoster(rosterRequest("DUTY_NEW_P1")));
        assertTrue(race.getMessage().contains("已有待复核"));
        verify(paymentConfigMapper, never()).updateIssueDutyRoster(any());
    }

    @Test
    void shouldRejectSameReviewerStaleBaselineAndProcessedReview() {
        PaymentIssueDutyRosterReviewEntity review = review("UPDATE", "stale-baseline");
        when(paymentConfigMapper.findIssueDutyRosterReviewForUpdate("DRR-001")).thenReturn(review);
        IllegalArgumentException sameReviewer = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper).approveIssueDutyRosterReview(
                        "DRR-001", action("maker-a", "同人复核")));
        assertTrue(sameReviewer.getMessage().contains("不能相同"));

        when(paymentConfigMapper.findIssueDutyRosterByCodeForUpdate("DUTY_CALLBACK_P1"))
                .thenReturn(roster("ENABLED"));
        IllegalArgumentException stale = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper).approveIssueDutyRosterReview(
                        "DRR-001", action("checker-b", "确认修改")));
        assertTrue(stale.getMessage().contains("已变化"));

        PaymentIssueDutyRosterReviewEntity processed = review("UPDATE", "baseline");
        processed.setReviewStatus("APPROVED");
        when(paymentConfigMapper.findIssueDutyRosterReviewForUpdate("DRR-DONE")).thenReturn(processed);
        IllegalArgumentException alreadyDone = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper).rejectIssueDutyRosterReview(
                        "DRR-DONE", action("checker-b", "重复处理")));
        assertTrue(alreadyDone.getMessage().contains("已处理"));
        verify(paymentConfigMapper, never()).updateIssueDutyRoster(any());
    }

    @Test
    void shouldRejectCandidateWithoutChangingRuntime() {
        PaymentIssueDutyRosterReviewEntity review = review("STATUS_CHANGE", "baseline");
        when(paymentConfigMapper.findIssueDutyRosterReviewForUpdate("DRR-001")).thenReturn(review);
        when(paymentConfigMapper.completeIssueDutyRosterReview(any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(paymentConfigMapper.findIssueDutyRosterReviews()).thenReturn(Collections.emptyList());

        new PaymentConfigServiceImpl(paymentConfigMapper).rejectIssueDutyRosterReview(
                "DRR-001", action("checker-b", "暂不调整"));

        verify(paymentConfigMapper).completeIssueDutyRosterReview(
                "DRR-001", "REJECTED", "danger", "checker-b", "暂不调整");
        verify(paymentConfigMapper, never()).insertIssueDutyRoster(any());
        verify(paymentConfigMapper, never()).updateIssueDutyRoster(any());
    }

    @Test
    void shouldTranslateConcurrentCreateConflictAndValidateCandidate() {
        PaymentIssueDutyRosterReviewEntity review = review("CREATE", null);
        review.setRosterCode("DUTY_NEW_P1");
        when(paymentConfigMapper.findIssueDutyRosterReviewForUpdate("DRR-001")).thenReturn(review);
        when(paymentConfigMapper.insertIssueDutyRoster(any()))
                .thenThrow(new DuplicateKeyException("uk_issue_duty_roster_code"));
        IllegalArgumentException conflict = assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper).approveIssueDutyRosterReview(
                        "DRR-001", action("checker-b", "确认新增")));
        assertTrue(conflict.getMessage().contains("已存在"));

        PaymentIssueDutyRosterUpsertRequestDTO invalidHoliday = rosterRequest("DUTY_NEW_P2");
        invalidHoliday.setHolidayStrategy("HOLIDAY_ONLY");
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .createIssueDutyRoster(invalidHoliday)).getMessage().contains("日期策略"));

        PaymentIssueDutyRosterUpsertRequestDTO invalidTimeout = rosterRequest("DUTY_NEW_P3");
        invalidTimeout.setEscalationTimeoutMinutes(Integer.valueOf(3));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .createIssueDutyRoster(invalidTimeout)).getMessage().contains("升级超时"));
    }

    private PaymentIssueDutyRosterStatusReviewRequestDTO statusRequest(String rosterCode, boolean enabled) {
        PaymentIssueDutyRosterStatusReviewRequestDTO request =
                new PaymentIssueDutyRosterStatusReviewRequestDTO();
        request.setRosterCode(rosterCode);
        request.setEnabled(Boolean.valueOf(enabled));
        request.setRequester("maker-a");
        request.setChangeReason("调整值班路由状态");
        return request;
    }

    private PaymentIssueDutyRosterReviewActionRequestDTO action(String reviewer, String comment) {
        PaymentIssueDutyRosterReviewActionRequestDTO request =
                new PaymentIssueDutyRosterReviewActionRequestDTO();
        request.setReviewer(reviewer);
        request.setReviewComment(comment);
        return request;
    }

    private PaymentIssueDutyRosterUpsertRequestDTO rosterRequest(String rosterCode) {
        PaymentIssueDutyRosterEntity roster = roster("ENABLED");
        PaymentIssueDutyRosterUpsertRequestDTO request = new PaymentIssueDutyRosterUpsertRequestDTO();
        request.setRosterCode(rosterCode);
        request.setIssueType(roster.getIssueType());
        request.setSeverity(roster.getSeverity());
        request.setResponsibilityGroup(roster.getResponsibilityGroup());
        request.setReceiver(roster.getReceiver());
        request.setNotifyChannels(roster.getNotifyChannels());
        request.setEscalationLevel(roster.getEscalationLevel());
        request.setEscalationReceiver(roster.getEscalationReceiver());
        request.setEscalationPolicy(roster.getEscalationPolicy());
        request.setEscalationTimeoutMinutes(roster.getEscalationTimeoutMinutes());
        request.setScheduleTag(roster.getScheduleTag());
        request.setEffectiveStartHour(roster.getEffectiveStartHour());
        request.setEffectiveEndHour(roster.getEffectiveEndHour());
        request.setWeekdayScope("5,1,3,3");
        request.setHolidayStrategy(roster.getHolidayStrategy());
        request.setEnabled(Boolean.TRUE);
        request.setRequester("maker-a");
        request.setChangeReason("调整异常告警值班路由");
        return request;
    }

    private PaymentIssueDutyRosterReviewEntity review(String operationType, String baseline) {
        PaymentIssueDutyRosterEntity roster = roster("ENABLED");
        PaymentIssueDutyRosterReviewEntity review = new PaymentIssueDutyRosterReviewEntity();
        review.setRequestNo("DRR-001");
        review.setRosterCode(roster.getRosterCode());
        review.setOperationType(operationType);
        review.setIssueType(roster.getIssueType());
        review.setSeverity(roster.getSeverity());
        review.setResponsibilityGroup(roster.getResponsibilityGroup());
        review.setReceiver(roster.getReceiver());
        review.setNotifyChannels(roster.getNotifyChannels());
        review.setEscalationLevel(roster.getEscalationLevel());
        review.setEscalationReceiver(roster.getEscalationReceiver());
        review.setEscalationPolicy(roster.getEscalationPolicy());
        review.setEscalationTimeoutMinutes(roster.getEscalationTimeoutMinutes());
        review.setScheduleTag(roster.getScheduleTag());
        review.setEffectiveStartHour(roster.getEffectiveStartHour());
        review.setEffectiveEndHour(roster.getEffectiveEndHour());
        review.setWeekdayScope(roster.getWeekdayScope());
        review.setHolidayStrategy(roster.getHolidayStrategy());
        review.setBaseFingerprint(baseline);
        review.setCandidateStatus(roster.getStatus());
        review.setCandidateStatusType(roster.getStatusType());
        review.setRequester("maker-a");
        review.setRequestReason("调整异常告警值班路由");
        review.setReviewStatus("PENDING_REVIEW");
        review.setReviewStatusType("warn");
        return review;
    }

    private PaymentIssueDutyRosterEntity roster(String status) {
        PaymentIssueDutyRosterEntity roster = new PaymentIssueDutyRosterEntity();
        roster.setRosterCode("DUTY_CALLBACK_P1");
        roster.setIssueType("待回调未收口");
        roster.setSeverity("P1");
        roster.setResponsibilityGroup("支付后端值班组");
        roster.setReceiver("支付后端值班");
        roster.setNotifyChannels("IN_APP,IM,SMS");
        roster.setEscalationLevel("L1");
        roster.setEscalationReceiver("支付技术负责人");
        roster.setEscalationPolicy("30分钟未确认升级支付技术负责人");
        roster.setEscalationTimeoutMinutes(Integer.valueOf(30));
        roster.setScheduleTag("交易链路白班");
        roster.setEffectiveStartHour(Integer.valueOf(0));
        roster.setEffectiveEndHour(Integer.valueOf(23));
        roster.setWeekdayScope("1,2,3,4,5");
        roster.setHolidayStrategy("WORKDAY_ONLY");
        roster.setStatus(status);
        roster.setStatusType("ENABLED".equals(status) ? "success" : "danger");
        return roster;
    }
}
