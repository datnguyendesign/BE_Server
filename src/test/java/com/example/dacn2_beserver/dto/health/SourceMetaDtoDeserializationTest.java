package com.example.dacn2_beserver.dto.health;

import com.example.dacn2_beserver.model.enums.DataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SourceMetaDtoDeserializationTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
    }

    @Test
    void lowercaseManualMapsToMANUAL() throws Exception {
        SourceMetaDto dto = mapper.readValue("{\"source\":\"manual\"}", SourceMetaDto.class);
        assertThat(dto.getSource()).isEqualTo(DataSource.MANUAL);
    }

    @Test
    void timerMapsToMANUAL() throws Exception {
        SourceMetaDto dto = mapper.readValue("{\"source\":\"timer\"}", SourceMetaDto.class);
        assertThat(dto.getSource()).isEqualTo(DataSource.MANUAL);
    }

    @Test
    void uppercaseMANUALMapsToMANUAL() throws Exception {
        SourceMetaDto dto = mapper.readValue("{\"source\":\"MANUAL\"}", SourceMetaDto.class);
        assertThat(dto.getSource()).isEqualTo(DataSource.MANUAL);
    }

    @Test
    void fullBodyWithTimerSourceDeserializesAndSourceIsMANUAL() throws Exception {
        String json = "{\"time\":{\"startAt\":\"2026-06-25T23:00:00Z\",\"endAt\":\"2026-06-26T07:00:00Z\"},"
                + "\"meta\":{\"source\":\"timer\"}}";
        CreateSleepSessionRequest req = mapper.readValue(json, CreateSleepSessionRequest.class);
        assertThat(req.getMeta()).isNotNull();
        assertThat(req.getMeta().getSource()).isEqualTo(DataSource.MANUAL);
    }

    @Test
    void bogusSourceMapsToNull() throws Exception {
        SourceMetaDto dto = mapper.readValue("{\"source\":\"bogus\"}", SourceMetaDto.class);
        assertThat(dto.getSource()).isNull();
    }
}
