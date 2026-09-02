package dev.devpanda.bamboocef.dev;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.devpanda.bamboocef.web.runtime.WebDebug;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows which pages are running and where their tools are.
 *
 * <p><b>Chromium's own tools, not reimplemented ones.</b> If remote debugging
 * is running, Chromium lists under {@code /json/list} every open page with
 * title, address and a tools address of its own. This here is only the
 * translation of that list into the chat — with the mapping that is missing
 * there: which page belongs to which board.
 *
 * <p><b>The mapping sits in the fragment.</b> Every surface appends its name
 * as {@code #fn-panel=…} to its address. Without that they are all called the
 * same in the list — the same page, the same title.
 *
 * <p><b>The fetch does not run on the render thread.</b> It does go to the
 * local machine, but a hanging port would otherwise hang the game. A short
 * thread fetches, the render thread writes.
 */
public final class WebDevTools {

    /** Where the list comes from. */
    private static final String LIST = "http://127.0.0.1:"
            + WebDebug.PORT + "/json/list";

    /** What you open in the browser to see everything. */
    private static final String FRONT = "http://127.0.0.1:"
            + WebDebug.PORT;

    private WebDevTools() {
    }

    /**
     * Writes the list into the chat.
     *
     * <p>To be called from the game; the answer comes a few milliseconds later
     * and runs back through {@code Minecraft.execute}.
     */
    public static void show() {
        if (!WebDebug.isRequested()) {
            say(Component.literal(
                            "Chromiums Fernwartung ist zu. Der Client muss mit "
                                    + "-Dfn.devtools=true starten — im Entwicklungslauf über "
                                    + "./gradlew runClient -Pdevtools.")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }
        Thread worker = new Thread(() -> {
            try {
                List<Component> lines = fetch();
                Minecraft.getInstance().execute(() -> lines.forEach(WebDevTools::say));
            } catch (Throwable broken) {
                Minecraft.getInstance().execute(() -> say(Component.literal(
                                "Die Liste ließ sich nicht holen: " + broken)
                        .withStyle(ChatFormatting.RED)));
            }
        }, "BambooCEF DevTools");
        worker.setDaemon(true);
        worker.start();
    }

    private static List<Component> fetch() throws Exception {
        HttpURLConnection connection =
                (HttpURLConnection) URI.create(LIST).toURL().openConnection();
        connection.setConnectTimeout(2_000);
        connection.setReadTimeout(5_000);
        JsonElement parsed;
        try (InputStreamReader reader = new InputStreamReader(
                connection.getInputStream(), StandardCharsets.UTF_8)) {
            parsed = JsonParser.parseReader(reader);
        } finally {
            connection.disconnect();
        }

        List<Component> lines = new ArrayList<>();
        JsonArray pages = parsed.isJsonArray() ? parsed.getAsJsonArray() : new JsonArray();
        if (pages.isEmpty()) {
            lines.add(Component.literal("Es läuft gerade keine Seite.")
                    .withStyle(ChatFormatting.GRAY));
            return lines;
        }
        lines.add(Component.literal("Offene Seiten (" + pages.size() + "):")
                .withStyle(ChatFormatting.WHITE));
        for (JsonElement element : pages) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject page = element.getAsJsonObject();
            String url = text(page, "url");
            String title = text(page, "title");
            String label = panelName(url);
            lines.add(Component.literal("  • ")
                    .append(Component.literal(label.isEmpty() ? title : label)
                            .withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("  " + shorten(url))
                            .withStyle(ChatFormatting.DARK_GRAY)));
        }
        lines.add(Component.literal("Werkzeuge öffnen: " + FRONT)
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.BLUE)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, FRONT))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Im Browser öffnen und die Seite auswählen")))));
        return lines;
    }

    /**
     * The name from the fragment, or empty.
     *
     * <p>A surface appends {@code #fn-panel=<name>} to its address when
     * opening; the name comes from that. A page without a fragment is not a
     * named surface — the editor, for instance.
     */
    private static String panelName(String url) {
        int at = url.indexOf("#fn-panel=");
        if (at < 0) {
            return "";
        }
        String raw = url.substring(at + "#fn-panel=".length());
        return java.net.URLDecoder.decode(raw, StandardCharsets.UTF_8);
    }

    /** Long file addresses are just noise in the chat. */
    private static String shorten(String url) {
        int cut = url.indexOf('#');
        String plain = cut < 0 ? url : url.substring(0, cut);
        int slash = plain.lastIndexOf('/');
        return slash < 0 ? plain : plain.substring(slash + 1);
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || !value.isJsonPrimitive() ? "" : value.getAsString();
    }

    private static void say(Component line) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.displayClientMessage(line, false);
        }
    }
}
