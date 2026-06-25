package com.example.dacn2_beserver.service.ai;

import com.example.dacn2_beserver.dto.ai.DailyAnalysisRequest;
import com.example.dacn2_beserver.dto.ai.DailyAnalysisResponse;
import com.example.dacn2_beserver.exception.ApiException;
import com.example.dacn2_beserver.model.health.DailyAggregate;
import com.example.dacn2_beserver.model.user.User;
import com.example.dacn2_beserver.model.user.UserGoals;
import com.example.dacn2_beserver.repository.AiFeedbackRepository;
import com.example.dacn2_beserver.repository.DailyAggregateRepository;
import com.example.dacn2_beserver.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAnalysisServiceTest {

    @Mock UserRepository userRepository;
    @Mock DailyAggregateRepository dailyAggregateRepository;
    @Mock AiFeedbackRepository aiFeedbackRepository;

    AiAnalysisService service;

    private User userWithGoals(Integer stepGoal) {
        return User.builder()
                .id("u1")
                .goals(UserGoals.builder().dailySteps(stepGoal).dailyWaterMl(2000).dailyCaloriesOut(500).build())
                .build();
    }

    @BeforeEach
    void wireScorer() {
        // ReadinessScorer is a real collaborator, not a mock; @InjectMocks won't supply it.
        service = new AiAnalysisService(aiFeedbackRepository, userRepository,
                dailyAggregateRepository, new ReadinessScorer());
    }

    @Test
    void usesDailyAggregateWhenPresent() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(userWithGoals(8000)));
        DailyAggregate agg = DailyAggregate.builder()
                .userId("u1").date(LocalDate.parse("2026-06-25"))
                .steps(8000).waterMl(2000).caloriesOut(500).sleepMinutes(480)
                .build();
        when(dailyAggregateRepository.findByUserIdAndDate("u1", LocalDate.parse("2026-06-25")))
                .thenReturn(Optional.of(agg));

        DailyAnalysisRequest req = new DailyAnalysisRequest("2026-06-25", null);
        DailyAnalysisResponse res = service.analyzeDaily("u1", req);

        assertThat(res.getReadinessScore()).isEqualTo(95);
        assertThat(res.getActionPlan()).anyMatch(s -> s.contains("đạt mục tiêu"));
    }

    @Test
    void fallsBackToSummaryWhenNoAggregate() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(userWithGoals(8000)));
        when(dailyAggregateRepository.findByUserIdAndDate("u1", LocalDate.parse("2026-06-25")))
                .thenReturn(Optional.empty());

        DailyAnalysisRequest req = new DailyAnalysisRequest("2026-06-25", Map.of("steps", 8000));
        DailyAnalysisResponse res = service.analyzeDaily("u1", req);

        // steps met (60 + 15 = 75), water/calo/sleep absent
        assertThat(res.getReadinessScore()).isEqualTo(75);
    }

    @Test
    void emptyDataReturns78() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(userWithGoals(8000)));
        when(dailyAggregateRepository.findByUserIdAndDate("u1", LocalDate.parse("2026-06-25")))
                .thenReturn(Optional.empty());

        DailyAnalysisRequest req = new DailyAnalysisRequest("2026-06-25", null);
        DailyAnalysisResponse res = service.analyzeDaily("u1", req);

        assertThat(res.getReadinessScore()).isEqualTo(78);
    }

    @Test
    void nullGoalUsesDefault() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(userWithGoals(null)));
        DailyAggregate agg = DailyAggregate.builder()
                .userId("u1").date(LocalDate.parse("2026-06-25")).steps(8000).build();
        when(dailyAggregateRepository.findByUserIdAndDate("u1", LocalDate.parse("2026-06-25")))
                .thenReturn(Optional.of(agg));

        DailyAnalysisRequest req = new DailyAnalysisRequest("2026-06-25", null);
        DailyAnalysisResponse res = service.analyzeDaily("u1", req);

        // default step goal 8000, met => 60 + 15 = 75
        assertThat(res.getReadinessScore()).isEqualTo(75);
    }

    @Test
    void unknownUserThrows() {
        when(userRepository.findById("ghost")).thenReturn(Optional.empty());
        DailyAnalysisRequest req = new DailyAnalysisRequest("2026-06-25", null);
        assertThatThrownBy(() -> service.analyzeDaily("ghost", req))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void keepsStaticDescriptiveSections() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(userWithGoals(8000)));
        when(dailyAggregateRepository.findByUserIdAndDate("u1", LocalDate.parse("2026-06-25")))
                .thenReturn(Optional.empty());
        DailyAnalysisResponse res = service.analyzeDaily("u1", new DailyAnalysisRequest("2026-06-25", null));
        assertThat(res.getTargetUsers()).isNotEmpty();
        assertThat(res.getDisclaimer()).isNotBlank();
        assertThat(res.getTargetUsers()).contains("Người bận rộn muốn theo dõi sức khỏe hằng ngày trong một app.");
        assertThat(res.getMissingForHealthApp()).contains("Phân quyền dữ liệu y tế, chính sách bảo mật và cảnh báo cấp cứu rõ ràng.");
        assertThat(res.getOptimizations()).contains("Thu thập feedback sau trải nghiệm để cải thiện model và UI.");
        assertThat(res.getDisclaimer()).isEqualTo("Thông tin AI chỉ để tham khảo sức khỏe tổng quát, không thay thế chẩn đoán hoặc điều trị y tế.");
    }

    @Test
    void unparseableDateDoesNotLeakIntoSummary() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(userWithGoals(8000)));
        lenient().when(dailyAggregateRepository.findByUserIdAndDate(eq("u1"), any()))
                .thenReturn(Optional.empty());

        DailyAnalysisResponse res = service.analyzeDaily("u1", new DailyAnalysisRequest("garbage-not-a-date", null));

        assertThat(res.getSummary()).doesNotContain("garbage-not-a-date");
    }

    @Test
    void partialMetricFromAggregateScoresBelowCap() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(userWithGoals(8000)));
        DailyAggregate agg = DailyAggregate.builder()
                .userId("u1").date(LocalDate.parse("2026-06-25")).steps(4000).build();
        when(dailyAggregateRepository.findByUserIdAndDate("u1", LocalDate.parse("2026-06-25")))
                .thenReturn(Optional.of(agg));

        DailyAnalysisResponse res = service.analyzeDaily("u1", new DailyAnalysisRequest("2026-06-25", null));

        // base 60 + round(0.5*15)=8, other metrics absent => 68
        assertThat(res.getReadinessScore()).isEqualTo(68);
    }
}
