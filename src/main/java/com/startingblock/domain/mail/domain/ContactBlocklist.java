package com.startingblock.domain.mail.domain;

import com.startingblock.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "contact_blocklist",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_contact_blocklist_announcement_contact", columnNames = {"announcement_id", "contact"})
        }
)
public class ContactBlocklist extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contact", nullable = false)
    private String contact;

    @Column(name = "announcement_id", nullable = false)
    private Long announcementId;

    public ContactBlocklist(final String contact, final Long announcementId) {
        this.contact = contact;
        this.announcementId = announcementId;
    }
}
