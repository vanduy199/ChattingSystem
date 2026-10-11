package com.ChattingSystem.user.entity;

import com.ChattingSystem.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Column(name = "firebase_uid", nullable = false, unique = true, length = 128)
    private String firebaseUid;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "avatar_object", length = 500)
    private String avatarObject;

    @Column(name = "phone", length = 20)
    private String phone;

    private User(String firebaseUid,
                 String email,
                 String fullName,
                 String avatarObject,
                 String phone) {

        this.firebaseUid = Objects.requireNonNull(firebaseUid);
        this.email = Objects.requireNonNull(email);
        this.fullName = Objects.requireNonNull(fullName);
        this.avatarObject = avatarObject;
        this.phone = phone;
    }

    public static User create(
            String firebaseUid,
            String email,
            String fullName,
            String avatarObject,
            String phone
    ) {
        return new User(
                firebaseUid,
                email,
                fullName,
                avatarObject,
                phone
        );
    }

    public void updateProfile(String fullName, String phone) {
        this.fullName = Objects.requireNonNull(fullName);
        this.phone = phone;
    }

    public void updateAvatar(String avatarObject) {
        this.avatarObject = avatarObject;
    }
}
