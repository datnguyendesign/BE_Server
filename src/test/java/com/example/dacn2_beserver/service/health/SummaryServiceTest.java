package com.example.dacn2_beserver.service.health;

import com.example.dacn2_beserver.dto.health.DailyAggregateResponse;
import com.example.dacn2_beserver.model.health.DailyAggregate;
import com.example.dacn2_beserver.repository.DailyAggregateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SummaryServiceTest {

    @Mock DailyAggregateRepository dailyAggregateRepository;
    @InjectMocks SummaryService service;

    @Test
    void getByDateMapsHeartRateFields() {
        LocalDate date = LocalDate.parse("2026-06-26");
        DailyAggregate agg = DailyAggregate.builder()
                .userId("u1").date(date)
                .avgHeartRate(80).maxHeartRate(100).minHeartRate(60)
                .build();
        when(dailyAggregateRepository.findByUserIdAndDate(eq("u1"), any()))
                .thenReturn(Optional.of(agg));

        DailyAggregateResponse res = service.getByDate("u1", date);

        assertThat(res.getAvgHeartRate()).isEqualTo(80);
        assertThat(res.getMaxHeartRate()).isEqualTo(100);
        assertThat(res.getMinHeartRate()).isEqualTo(60);
    }
}
