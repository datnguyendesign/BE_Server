package com.example.dacn2_beserver.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "ratelimit")
public class RateLimitProperties {

    private boolean enabled = true;

    private Chat chat = new Chat();
    private Media media = new Media();

    @Getter
    @Setter
    public static class Chat {
        private Send send = new Send();

        @Getter
        @Setter
        public static class Send {
            private long limit = 10;
            private long windowSeconds = 10;
        }
    }

    @Getter
    @Setter
    public static class Media {
        private Presign presign = new Presign();

        @Getter
        @Setter
        public static class Presign {
            private long limit = 20;
            private long windowSeconds = 60;
        }
    }
}