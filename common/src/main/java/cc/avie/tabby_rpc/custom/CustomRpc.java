package cc.avie.tabby_rpc.custom;

import cc.avie.tabby_rpc.TabbyRPC;
import cc.avie.tabby_rpc.platform.Services;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Texts, images, buttons and the Discord application that a modpack sets in the {@code config/custom_rpc} folder.
 * Every value is optional, whatever is missing keeps the built-in default.
 */
public final class CustomRpc {
    public static final String FOLDER = "custom_rpc";
    private static final String SETTINGS_FILE = "settings.json";
    public static final Set<String> PLACEHOLDERS = Set.of(
            "player", "loader", "minecraft", "world", "server", "players", "max_players");
    // Limits Discord puts on activities.
    private static final int MAX_BUTTONS = 2;
    private static final int MAX_BUTTON_LABEL = 32;

    private static final CustomRpc NONE = new CustomRpc(null, null, null, new EnumMap<>(Situation.class));
    private static volatile CustomRpc current = NONE;

    private final @Nullable String applicationId;
    private final @Nullable String name;
    private final @Nullable List<Button> buttons;
    private final Map<Situation, Texts> situations;

    private CustomRpc(@Nullable String applicationId, @Nullable String name, @Nullable List<Button> buttons,
                      Map<Situation, Texts> situations) {
        this.applicationId = applicationId;
        this.name = name;
        this.buttons = buttons;
        this.situations = situations;
    }

    public static CustomRpc get() {
        return current;
    }

    /**
     * Reads the folder again. Broken files and values are reported in the log and left out.
     *
     * @return the number of problems that were found
     */
    public static int load() {
        Path folder = Services.PLATFORM.getConfigDir().resolve(FOLDER);
        if (!Files.isDirectory(folder)) {
            current = NONE;
            return 0;
        }

        Loader loader = new Loader(folder);
        current = loader.load();
        return loader.problems;
    }

    /**
     * @return the application to connect to instead of Tabby RPC's own one
     */
    public @Nullable String getApplicationId() {
        return this.applicationId;
    }

    public @Nullable String getName() {
        return this.name;
    }

    /**
     * @return the buttons that replace the download button, possibly none at all
     */
    public @Nullable List<Button> getButtons() {
        return this.buttons;
    }

    public @Nullable Texts getTexts(Situation situation) {
        return this.situations.get(situation);
    }

    /**
     * Templates for one situation. A missing template keeps the default.
     */
    public record Texts(@Nullable String details, @Nullable String state, @Nullable String largeImage,
                        @Nullable String largeText) {
    }

    public record Button(String label, String url) {
    }

    private static final class Loader {
        private final Path folder;
        private int problems;
        private String file = "";

        private Loader(Path folder) {
            this.folder = folder;
        }

        private CustomRpc load() {
            String applicationId = null;
            String name = null;
            List<Button> buttons = null;
            JsonObject settings = this.read(SETTINGS_FILE);
            if (settings != null) {
                applicationId = this.string(settings, "application_id");
                if (applicationId != null && !isNumber(applicationId)) {
                    this.problem("application_id is not the ID of a Discord application");
                    applicationId = null;
                }
                name = this.string(settings, "name");
                buttons = this.buttons(settings);
            }

            Map<Situation, Texts> situations = new EnumMap<>(Situation.class);
            for (Situation situation : Situation.values()) {
                JsonObject json = this.read(situation.getFileName());
                if (json != null) {
                    situations.put(situation, new Texts(this.template(json, "details"),
                            this.template(json, "state"), this.template(json, "large_image"),
                            this.template(json, "large_text")));
                }
            }

            TabbyRPC.LOGGER.info("Loaded the custom presence from {}", this.folder);
            return new CustomRpc(applicationId, name, buttons, situations);
        }

        private @Nullable JsonObject read(String file) {
            this.file = file;
            Path path = this.folder.resolve(file);
            if (!Files.isRegularFile(path)) {
                return null;
            }

            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                if (JsonParser.parseReader(reader) instanceof JsonObject object) {
                    return object;
                }
                this.problem("the file does not contain a JSON object");
            } catch (IOException | JsonParseException e) {
                this.problem("the file could not be read: " + e.getMessage());
            }
            return null;
        }

        private @Nullable List<Button> buttons(JsonObject settings) {
            JsonElement element = settings.get("buttons");
            if (element == null) {
                return null;
            }
            if (!(element instanceof JsonArray array)) {
                this.problem("buttons is not a list");
                return null;
            }

            List<Button> buttons = new ArrayList<>();
            for (JsonElement entry : array) {
                if (buttons.size() == MAX_BUTTONS) {
                    this.problem("Discord shows at most " + MAX_BUTTONS + " buttons");
                    break;
                }

                String label = entry instanceof JsonObject button ? this.string(button, "label") : null;
                String url = entry instanceof JsonObject button ? this.string(button, "url") : null;
                if (label == null || url == null || label.length() > MAX_BUTTON_LABEL
                        || !(url.startsWith("https://") || url.startsWith("http://"))) {
                    this.problem("a button needs a label of at most " + MAX_BUTTON_LABEL
                            + " characters and a URL starting with https://");
                    continue;
                }
                buttons.add(new Button(label, url));
            }
            return List.copyOf(buttons);
        }

        private @Nullable String template(JsonObject json, String key) {
            String template = this.string(json, key);
            if (template == null) {
                return null;
            }

            for (int start = template.indexOf('{'); start >= 0; start = template.indexOf('{', start + 1)) {
                int end = template.indexOf('}', start);
                if (end < 0) {
                    break;
                }
                String placeholder = template.substring(start + 1, end);
                if (!PLACEHOLDERS.contains(placeholder)) {
                    this.problem(key + " uses the unknown placeholder {" + placeholder + "}");
                }
            }
            return template;
        }

        private @Nullable String string(JsonObject json, String key) {
            JsonElement element = json.get(key);
            if (element == null || element.isJsonNull()) {
                return null;
            }
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                String value = element.getAsString();
                if (!value.isBlank()) {
                    return value;
                }
            }
            this.problem(key + " has to be text");
            return null;
        }

        private void problem(String message) {
            this.problems++;
            TabbyRPC.LOGGER.warn("{}/{}: {}", FOLDER, this.file, message);
        }

        private static boolean isNumber(String text) {
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) < '0' || text.charAt(i) > '9') {
                    return false;
                }
            }
            return true;
        }
    }
}
