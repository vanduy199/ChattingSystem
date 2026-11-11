package com.ChattingSystem.user.entity;

import com.ChattingSystem.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    @Column(name = "firebase_uid", unique = true, length = 128)
    private String firebaseUid;

    @Column(name = "email", unique = true, length = 255)
    private String email;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "avatar_object", length = 500)
    private String avatarObject;

    @Column(name = "phone", length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

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
        this.role = Role.USER;
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

    public static User createGuest(String fullName) {
        User guest = new User();
        guest.fullName = (fullName != null && !fullName.isBlank()) ? fullName.trim() : "Khách";
        guest.role = Role.GUEST;
        return guest;
    }

    public void upgradeToUser(String firebaseUid, String email, String fullName) {
        if (!isGuest()) {
            throw new IllegalStateException("Chỉ tài khoản khách mới có thể nâng cấp thành tài khoản thật");
        }
        this.firebaseUid = Objects.requireNonNull(firebaseUid);
        this.email = Objects.requireNonNull(email);
        if (fullName != null && !fullName.isBlank()) {
            this.fullName = fullName.trim();
        } else if (this.fullName == null || this.fullName.isBlank()) {
            this.fullName = "người dùng mới";
        }
        this.role = Role.USER;
    }

    public void updateProfile(String fullName, String phone) {
        this.fullName = Objects.requireNonNull(fullName);
        this.phone = phone;
    }

    public void updateAvatar(String avatarObject) {
        this.avatarObject = avatarObject;
    }

    public void changeRole(Role role) {
        this.role = Objects.requireNonNull(role);
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean isGuest() {
        return role == Role.GUEST;
    }
}
