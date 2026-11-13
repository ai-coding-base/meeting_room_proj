package com.company.booking;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 一次性工具：生成 8 个种子用户的 BCrypt 哈希，便于种子 SQL。
 * <p>
 * 使用方式：{@code mvn -pl . test -Dtest=PasswordHashGenerator}
 * <p>
 * v1.0 部署完成后可删除。
 */
class PasswordHashGenerator {

    @Test
    void generate() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String[] users = {"alice", "bob", "carol", "david", "eve", "frank", "grace", "helen"};
        String password = "password123";
        System.out.println("===== BCRYPT HASHES (password=" + password + ") =====");
        for (String u : users) {
            System.out.println(u + ":" + encoder.encode(password));
        }
        System.out.println("===== END =====");
    }
}
