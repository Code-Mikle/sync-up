package com.mikle.syncup.data;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 离线导入 data/users.json 到 user 表。JSON 中的标签名称会转换为 tag 表中的标准标签 id。
 * 数据库连接信息从项目根目录的 .env 读取，重复 userAccount 默认跳过，已有用户资料不会被覆盖。
 */
public final class ImportUserData {

    private static final Path ENV_FILE = Path.of(".env");
    private static final Path USER_DATA_FILE = Path.of("data", "users.json");
    private static final int BATCH_SIZE = 200;
    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private static final String INSERT_USER_SQL = """
            insert into `user` (
                username, userAccount, avatarUrl, gender, userPassword, phone, email,
                city, userStatus, userRole, tagIds, profile
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on duplicate key update id = id
            """;

    public static void main(String[] args) {
        try {
            Map<String, String> env = loadEnvFile(ENV_FILE);
            DatabaseConfig databaseConfig = DatabaseConfig.from(env);
            JSONArray userDataEntries = readUserData(USER_DATA_FILE);
            ImportSummary summary = importUsers(userDataEntries, databaseConfig);
            System.out.printf(
                    "导入完成：总计 %d，新增 %d，已存在跳过 %d，参数不合法跳过 %d%n",
                    summary.total(), summary.inserted(), summary.duplicateSkipped(), summary.invalidSkipped()
            );
        } catch (Exception e) {
            System.err.printf("用户导入失败：%s%n", e.getMessage());
            throw new IllegalStateException("用户导入失败", e);
        }
    }

    private static JSONArray readUserData(Path userDataFile) {
        if (!Files.isRegularFile(userDataFile)) {
            throw new IllegalArgumentException("找不到用户数据文件：" + userDataFile.toAbsolutePath());
        }
        return JSONUtil.readJSONArray(userDataFile.toFile(), StandardCharsets.UTF_8);
    }

    private static ImportSummary importUsers(JSONArray userDataEntries, DatabaseConfig databaseConfig) throws SQLException {
        int inserted = 0;
        int duplicateSkipped = 0;
        int invalidSkipped = 0;
        int pendingBatchSize = 0;

        try (Connection connection = DriverManager.getConnection(
                databaseConfig.url(), databaseConfig.username(), databaseConfig.password());
             PreparedStatement statement = connection.prepareStatement(INSERT_USER_SQL)) {
            connection.setAutoCommit(false);
            try {
                Map<String, Long> tagIdByName = loadEnabledTagIds(connection);
                for (int index = 0; index < userDataEntries.size(); index++) {
                    JSONObject userData = userDataEntries.getJSONObject(index);
                    if (!isValidUserData(userData)) {
                        invalidSkipped++;
                        System.err.printf("跳过第 %d 条数据：userAccount 或 userPassword 为空%n", index + 1);
                        continue;
                    }

                    bindUser(statement, userData, tagIdByName);
                    statement.addBatch();
                    pendingBatchSize++;
                    if (pendingBatchSize == BATCH_SIZE) {
                        BatchResult batchResult = executeBatch(statement);
                        inserted += batchResult.inserted();
                        duplicateSkipped += batchResult.duplicateSkipped();
                        pendingBatchSize = 0;
                    }
                }
                if (pendingBatchSize > 0) {
                    BatchResult batchResult = executeBatch(statement);
                    inserted += batchResult.inserted();
                    duplicateSkipped += batchResult.duplicateSkipped();
                }
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        }
        return new ImportSummary(userDataEntries.size(), inserted, duplicateSkipped, invalidSkipped);
    }

    private static boolean isValidUserData(JSONObject userData) {
        return userData != null
                && StringUtils.isNotBlank(userData.getStr("userAccount"))
                && StringUtils.isNotBlank(userData.getStr("userPassword"));
    }

    private static void bindUser(PreparedStatement statement, JSONObject userData,
                                 Map<String, Long> tagIdByName) throws SQLException {
        statement.setString(1, trimToNull(userData.getStr("username")));
        statement.setString(2, userData.getStr("userAccount").trim());
        statement.setString(3, trimToNull(userData.getStr("avatarUrl")));
        statement.setObject(4, userData.getInt("gender"));
        statement.setString(5, PASSWORD_ENCODER.encode(userData.getStr("userPassword")));
        statement.setString(6, trimToNull(userData.getStr("phone")));
        statement.setString(7, trimToNull(userData.getStr("email")));
        statement.setString(8, trimToNull(userData.getStr("city")));
        statement.setInt(9, userData.getInt("userStatus", 0));
        statement.setInt(10, 0);
        statement.setString(11, toTagIdsJson(userData.get("tags"), tagIdByName));
        statement.setString(12, trimToNull(userData.getStr("profile")));
    }

    private static BatchResult executeBatch(PreparedStatement statement) throws SQLException {
        int inserted = 0;
        int duplicateSkipped = 0;
        for (int updateCount : statement.executeBatch()) {
            if (updateCount == 0) {
                duplicateSkipped++;
            } else {
                // MySQL may return SUCCESS_NO_INFO for a successful batch item.
                inserted++;
            }
        }
        return new BatchResult(inserted, duplicateSkipped);
    }

    private static Map<String, Long> loadEnabledTagIds(Connection connection) throws SQLException {
        Map<String, Long> tagIdByName = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "select id, name from tag where status = 1 and isDelete = 0");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                tagIdByName.put(resultSet.getString("name"), resultSet.getLong("id"));
            }
        }
        if (tagIdByName.isEmpty()) {
            throw new IllegalStateException("未查询到启用的标准标签，请先初始化 tag 表数据");
        }
        return tagIdByName;
    }

    private static String toTagIdsJson(Object tags, Map<String, Long> tagIdByName) {
        if (tags == null) {
            return "[]";
        }
        JSONArray tagNames = JSONUtil.parseArray(tags);
        LinkedHashSet<Long> tagIds = new LinkedHashSet<>();
        for (Object tagNameValue : tagNames) {
            String tagName = StringUtils.trimToNull(String.valueOf(tagNameValue));
            if (tagName == null) {
                continue;
            }
            Long tagId = tagIdByName.get(tagName);
            if (tagId == null) {
                throw new IllegalArgumentException("未找到标签对应的标准标签 id：" + tagName);
            }
            tagIds.add(tagId);
        }
        return JSONUtil.toJsonStr(tagIds);
    }

    private static String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    private static Map<String, String> loadEnvFile(Path envFile) throws IOException {
        Map<String, String> values = new HashMap<>();
        if (!Files.exists(envFile)) {
            return values;
        }
        List<String> lines = Files.readAllLines(envFile, StandardCharsets.UTF_8);
        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                continue;
            }
            int separatorIndex = trimmedLine.indexOf('=');
            if (separatorIndex <= 0) {
                continue;
            }
            String key = trimmedLine.substring(0, separatorIndex).trim();
            String value = unquote(trimmedLine.substring(separatorIndex + 1).trim());
            values.put(key, value);
        }
        return values;
    }

    private static String unquote(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private record DatabaseConfig(String url, String username, String password) {

        private static DatabaseConfig from(Map<String, String> env) {
            String host = getConfigValue(env, "LOCAL_MYSQL_HOST", "localhost");
            String port = getConfigValue(env, "LOCAL_MYSQL_PORT", "3306");
            String username = getConfigValue(env, "LOCAL_MYSQL_USERNAME", "root");
            String password = getConfigValue(env, "LOCAL_MYSQL_PASSWORD", "");
            String url = "jdbc:mysql://%s:%s/sync_up_db?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC"
                    .formatted(host, port);
            return new DatabaseConfig(url, username, password);
        }

        private static String getConfigValue(Map<String, String> env, String key, String defaultValue) {
            String systemValue = System.getenv(key);
            if (StringUtils.isNotBlank(systemValue)) {
                return systemValue;
            }
            String fileValue = env.get(key);
            return fileValue == null ? defaultValue : fileValue;
        }
    }

    private record BatchResult(int inserted, int duplicateSkipped) {
    }

    private record ImportSummary(int total, int inserted, int duplicateSkipped, int invalidSkipped) {
    }
}
