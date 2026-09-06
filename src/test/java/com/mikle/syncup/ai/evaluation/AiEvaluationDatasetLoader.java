package com.mikle.syncup.ai.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class AiEvaluationDatasetLoader {

    private static final String DATA_DIRECTORY = "data/ai-evaluation";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Path projectRoot = findProjectRoot();

    UsersDocument users() {
        return read("users-v1.json", UsersDocument.class);
    }

    ProfilesDocument profiles() {
        return read("user-profiles-v1.json", ProfilesDocument.class);
    }

    TeamsDocument teams() {
        return read("teams-v1.json", TeamsDocument.class);
    }

    JsonNode tree(String fileName) {
        return read(fileName, JsonNode.class);
    }

    String text(String relativePath) {
        try {
            return Files.readString(projectRoot.resolve(relativePath));
        } catch (IOException e) {
            throw new IllegalStateException("cannot read evaluation file: " + relativePath, e);
        }
    }

    private <T> T read(String fileName, Class<T> type) {
        Path path = projectRoot.resolve(DATA_DIRECTORY).resolve(fileName);
        try {
            return objectMapper.readValue(path.toFile(), type);
        } catch (IOException e) {
            throw new IllegalStateException("cannot parse evaluation dataset: " + path, e);
        }
    }

    private Path findProjectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isDirectory(current.resolve(DATA_DIRECTORY))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("cannot locate " + DATA_DIRECTORY + " from current directory");
    }

    record UsersDocument(String datasetVersion, String description,
                         UserDefaults defaults, List<UserSeed> users) {
    }

    record UserDefaults(String userPassword, Integer userStatus, Integer userRole) {
    }

    record UserSeed(String username, String userAccount, Integer gender, String city,
                    List<String> tags, String profile) {
    }

    record ProfilesDocument(String datasetVersion, String description, List<ProfileSeed> profiles) {
    }

    record ProfileSeed(String userAccount,
                       String activityPreferenceText,
                       String socialPersonalityText,
                       String partnerPreferenceText,
                       String activityConstraintHabitText,
                       String aiInteractionPreferenceText) {
    }

    record TeamsDocument(String datasetVersion, String description,
                         TeamDefaults defaults, List<TeamSeed> teams) {
    }

    record TeamDefaults(Integer status, Integer durationMinutes, Integer expireAfterDays) {
    }

    record TeamSeed(String code, String name, String description, String ownerAccount,
                    Integer activityCategory, String activityType, String city, String district,
                    Integer startAfterDays, String startTimeOfDay, Integer expireAfterDays,
                    Integer durationMinutes, BigDecimal budgetPerPerson, String skillLevel,
                    Integer maxNum, Integer status, List<String> memberAccounts) {
    }
}
