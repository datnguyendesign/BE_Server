package com.example.dacn2_beserver.service.health;

import org.springframework.stereotype.Component;

/**
 * Ước lượng phân bổ giai đoạn ngủ từ tổng thời lượng (khi không có dữ liệu segment thật).
 * Thuần, không I/O. Tỷ lệ người lớn điển hình: deep 13%, REM 22%, light 60%, awake 5%.
 * light hấp thụ phần dư làm tròn để 4 giá trị cộng đúng bằng total.
 */
@Component
public class SleepStageEstimator {

    public record Stages(int deep, int rem, int light, int awake) {}

    public Stages estimate(int totalMinutes) {
        int total = Math.max(0, totalMinutes);
        int deep = (int) Math.round(total * 0.13);
        int rem = (int) Math.round(total * 0.22);
        int awake = (int) Math.round(total * 0.05);
        int light = total - deep - rem - awake;
        if (light < 0) light = 0;
        return new Stages(deep, rem, light, awake);
    }
}
