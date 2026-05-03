package com.github.fligneul.debtplugin.debt.service.json;

import com.github.fligneul.debtplugin.debt.model.Complexity;
import com.github.fligneul.debtplugin.debt.model.DebtItem;
import com.github.fligneul.debtplugin.debt.model.Relationship;
import com.github.fligneul.debtplugin.debt.model.Risk;
import com.github.fligneul.debtplugin.debt.model.Status;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class ADebtSerializer {

    protected final Gson gson;

    public ADebtSerializer() {
        this.gson = new GsonBuilder()
                .registerTypeAdapter(DebtItem.class, new DebtItemDeserializer())
                .setPrettyPrinting()
                .create();
    }

    private static final class DebtItemDeserializer implements JsonDeserializer<DebtItem> {
        @Override
        public DebtItem deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();

            // New fields (backward compatible)
            String id = getAsString(obj, "id", null);

            // Core fields
            String file = getAsString(obj, "file", "");
            int line = getAsInt(obj, "line", 1);
            String title = getAsString(obj, "title", "");
            String description = getAsString(obj, "description", "");
            String username = getAsString(obj, "username", "");
            int wantedLevel = getAsInt(obj, "wantedLevel", 3);

            Complexity complexity = parseEnum(obj, "complexity", Complexity.Medium, Complexity.class);
            Status status = parseEnum(obj, "status", Status.Submitted, Status.class);
            String priority = getAsString(obj, "priority", "");
            Risk risk = parseEnum(obj, "risk", Risk.Medium, Risk.class);

            String targetVersion = getAsString(obj, "targetVersion", "");
            String comment = getAsString(obj, "comment", "");
            int estimation = getAsInt(obj, "estimation", 0);
            String jira = getAsString(obj, "jira", "");
            String type = getAsString(obj, "type", "");

            // Optional, backward-compatible: prefer currentModule, fallback to legacy moduleParent
            String currentModule = getAsString(obj, "currentModule", null);
            if (currentModule == null || currentModule.isBlank()) {
                currentModule = getAsString(obj, "moduleParent", "");
            }

            // Parse links map if present (new format: Map<String, List<Relationship>>)
            Map<String, Relationship> links = new LinkedHashMap<>();
            JsonElement linksEl = obj.get("links");
            if (linksEl != null && linksEl.isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : linksEl.getAsJsonObject().entrySet()) {
                    String key = e.getKey();
                    JsonElement val = e.getValue();
                    try {
                        Relationship relationship = null;
                        if (val != null && !val.isJsonNull()) {
                            if (val.isJsonArray()) {
                                for (JsonElement el : val.getAsJsonArray()) {
                                    try {
                                        if (el != null && !el.isJsonNull()) {
                                            relationship = Relationship.valueOf(el.getAsString());
                                        }
                                    } catch (Exception ignoreEach) {
                                    }
                                }
                            } else if (val.isJsonPrimitive()) {
                                // Backward compatibility with old single value
                                String relStr = val.getAsString();
                                if (relStr != null && !relStr.isBlank()) {
                                    relationship = Relationship.valueOf(relStr);
                                }
                            }
                        }
                        if (relationship != null) {
                            links.put(key, relationship);
                        }
                    } catch (Exception ignore) {
                        // skip malformed entries
                    }
                }
            }

            final long creationDate = getAsLong(obj, "creationDate", 0);
            final long updateDate = getAsLong(obj, "updateDate", 0);

            final DebtItem.Builder builder = DebtItem.newBuilder(false)
                    .withFile(file)
                    .withLine(line)
                    .withTitle(title)
                    .withDescription(description)
                    .withUsername(username)
                    .withWantedLevel(wantedLevel)
                    .withComplexity(complexity)
                    .withStatus(status)
                    .withPriority(priority)
                    .withRisk(risk)
                    .withTargetVersion(targetVersion)
                    .withComment(comment)
                    .withEstimation(estimation)
                    .withCurrentModule(currentModule)
                    .withLinks(links)
                    .withJira(jira)
                    .withType(type)
                    .withCreateDate(creationDate)
                    .withUpdateDate(updateDate);
            if (id != null && !id.isBlank()) {
                builder.withId(id);
            }
            return builder.build();
        }

        private static String getAsString(JsonObject obj, String key, String def) {
            JsonElement jsonElement = obj.get(key);
            return jsonElement == null || jsonElement.isJsonNull() ? def : jsonElement.getAsString();
        }

        private static int getAsInt(JsonObject obj, String key, int def) {
            try {
                JsonElement jsonElement = obj.get(key);
                return jsonElement == null || jsonElement.isJsonNull() ? def : jsonElement.getAsInt();
            } catch (Exception ex) {
                return def;
            }
        }

        private static long getAsLong(JsonObject obj, String key, long def) {
            try {
                JsonElement jsonElement = obj.get(key);
                return jsonElement == null || jsonElement.isJsonNull() ? def : jsonElement.getAsLong();
            } catch (Exception ex) {
                return def;
            }
        }

        private static <E extends Enum<E>> E parseEnum(JsonObject obj, String key, E def, Class<E> enumType) {
            try {
                String string = getAsString(obj, key, null);
                if (string == null || string.isBlank()) return def;
                return Enum.valueOf(enumType, string);
            } catch (Exception ex) {
                return def;
            }
        }
    }
}
