package com.example.dacn2_beserver.model.auth;

import com.example.dacn2_beserver.model.enums.LinkTicketStatus;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("link_tickets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkTicket {

    @Id
    private String id; // "lt_xxx"

    @Indexed
    private String targetUserId;

    private String googleSub;
    private String googleEmail;
    private Boolean googleEmailVerified;
    private String name;
    private String picture;

    @Builder.Default
    private LinkTicketStatus status = LinkTicketStatus.PENDING;

    @Indexed(name = "link_ticket_expires_ttl", expireAfter = "0s")
    @Builder.Default
    private Instant expiresAt = Instant.now().plusSeconds(10 * 60);

    @Builder.Default
    private Instant createdAt = Instant.now();
    private Instant confirmedAt;
    private Instant rejectedAt;
}