package com.smarthome.online.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DBUtil {
    private static final Logger logger = LoggerFactory.getLogger(DBUtil.class);
    private String url;
    private String username;
    private String password;

    public DBUtil() {
        // 1. 配置
        this.url = "jdbc:mysql://hadoop102:3306/ha_device_log?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
        this.username = "root";
        this.password = "123456";

        // 2. 加载MySQL驱动
        try {
            Class.forName("com.mysql.jdbc.Driver");
            logger.info("MySQL驱动加载成功");
        } catch (ClassNotFoundException e) {
            logger.error("加载MySQL驱动失败", e);
            throw new RuntimeException("MySQL驱动加载失败", e);
        }

        // 3. 非空校验：如果关键配置为null，直接抛出异常
        if (this.url == null || this.url.trim().isEmpty()) {
            throw new RuntimeException("DBUtil初始化失败：url为null或空");
        }
        if (this.username == null || this.username.trim().isEmpty()) {
            throw new RuntimeException("DBUtil初始化失败：username为null或空");
        }
        if (this.password == null) { // 密码允许为空
            logger.warn("DBUtil初始化警告：password为null");
        }

        logger.info("DBUtil初始化完成，url={}, username={}", this.url, this.username);
    }

    // 获取数据库连接
    public Connection getConnection() throws SQLException {
        logger.debug("尝试获取MySQL连接：url={}, username={}", this.url, this.username);
        // 此时url一定非null，因为构造方法做了校验
        Connection conn = DriverManager.getConnection(this.url, this.username, this.password);
        logger.info("MySQL连接获取成功：{}", conn);
        return conn;
    }

    // 关闭资源
    public static void close(Connection conn, PreparedStatement pstmt, ResultSet rs) {
        try {
            if (rs != null) rs.close();
            if (pstmt != null) pstmt.close();
            if (conn != null) conn.close();
            logger.debug("MySQL资源关闭完成");
        } catch (SQLException e) {
            logger.error("MySQL资源关闭失败", e);
        }
    }
}