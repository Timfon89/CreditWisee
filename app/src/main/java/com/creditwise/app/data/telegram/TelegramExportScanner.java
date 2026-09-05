package com.creditwise.app.data.telegram;

import android.util.JsonReader;
import android.util.JsonToken;

import com.creditwise.app.data.model.TelegramFlag;
import com.creditwise.app.data.model.TelegramScanResult;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Streams a Telegram Desktop export ({@code result.json}) and collects financial-relevant
 * phrases from the account owner's own messages. Purely informational — never feeds scoring.
 */
public final class TelegramExportScanner {

    private static final int MAX_FLAGS = 60;
    private static final int MAX_PER_CATEGORY = 10;
    private static final int SNIPPET_BEFORE = 45;
    private static final int SNIPPET_AFTER = 90;

    private static final Map<TelegramFlag.Category, String[]> KEYWORDS = buildKeywords();

    private long ownUserId = -1;
    private boolean channelExport;
    private TelegramScanResult result;
    private final Map<TelegramFlag.Category, Integer> perCategory =
            new EnumMap<>(TelegramFlag.Category.class);

    public TelegramScanResult scan(InputStream in) throws IOException {
        result = new TelegramScanResult();
        perCategory.clear();
        ownUserId = -1;
        channelExport = false;
        try (JsonReader reader = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            reader.setLenient(true);
            JsonToken first = reader.peek();
            if (first == JsonToken.BEGIN_OBJECT) {
                readRoot(reader);
            } else {
                throw new IOException("Unexpected JSON root");
            }
        }
        result.ownMessagesIdentified = ownUserId > 0 || channelExport;
        if (!result.ownMessagesIdentified) {
            result.warnings.add("need_full_export");
        }
        return result;
    }

    private void readRoot(JsonReader reader) throws IOException {
        String topName = "канал";
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case "personal_information":
                    ownUserId = readUserId(reader);
                    break;
                case "chats":
                    readChatsContainer(reader);
                    break;
                case "type": {
                    String type = reader.nextString();
                    // a broadcast channel export contains only the author's own posts
                    channelExport = type != null
                            && (type.contains("channel") || type.equals("saved_messages"));
                    break;
                }
                case "name":
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull();
                    } else {
                        topName = reader.nextString();
                    }
                    break;
                case "messages":
                    // single-chat / single-channel export at the top level
                    readMessages(reader, topName);
                    break;
                default:
                    reader.skipValue();
            }
        }
        reader.endObject();
    }

    private long readUserId(JsonReader reader) throws IOException {
        long id = -1;
        reader.beginObject();
        while (reader.hasNext()) {
            String k = reader.nextName();
            if ("user_id".equals(k)) {
                id = reader.nextLong();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return id;
    }

    private void readChatsContainer(JsonReader reader) throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            String k = reader.nextName();
            if ("list".equals(k)) {
                reader.beginArray();
                while (reader.hasNext()) {
                    readChat(reader);
                }
                reader.endArray();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
    }

    private void readChat(JsonReader reader) throws IOException {
        String chatName = "чат";
        reader.beginObject();
        while (reader.hasNext()) {
            String k = reader.nextName();
            switch (k) {
                case "name":
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull();
                    } else {
                        chatName = reader.nextString();
                    }
                    break;
                case "messages":
                    readMessages(reader, chatName);
                    break;
                default:
                    reader.skipValue();
            }
        }
        reader.endObject();
    }

    private void readMessages(JsonReader reader, String chatName) throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            readMessage(reader, chatName);
        }
        reader.endArray();
    }

    private void readMessage(JsonReader reader, String chatName) throws IOException {
        String type = null;
        String fromId = null;
        String date = null;
        StringBuilder text = new StringBuilder();

        reader.beginObject();
        while (reader.hasNext()) {
            String k = reader.nextName();
            switch (k) {
                case "type":
                    type = reader.nextString();
                    break;
                case "from_id":
                    fromId = reader.peek() == JsonToken.NULL ? nullString(reader) : reader.nextString();
                    break;
                case "date":
                    date = reader.nextString();
                    break;
                case "text":
                    readText(reader, text);
                    break;
                default:
                    reader.skipValue();
            }
        }
        reader.endObject();

        result.messagesScanned++;
        if (!"message".equals(type)) return;
        if (text.length() == 0) return;

        boolean identified = ownUserId > 0 || channelExport;
        boolean own = (ownUserId > 0 && fromId != null && fromId.equals("user" + ownUserId))
                || channelExport;
        if (own) result.ownMessages++;
        if (identified && !own) return; // full export: skip other people's messages

        scanText(text.toString(), date, chatName);
    }

    private static String nullString(JsonReader reader) throws IOException {
        reader.nextNull();
        return null;
    }

    private void readText(JsonReader reader, StringBuilder out) throws IOException {
        JsonToken t = reader.peek();
        if (t == JsonToken.STRING) {
            out.append(reader.nextString());
        } else if (t == JsonToken.BEGIN_ARRAY) {
            reader.beginArray();
            while (reader.hasNext()) {
                JsonToken inner = reader.peek();
                if (inner == JsonToken.STRING) {
                    out.append(reader.nextString()).append(' ');
                } else if (inner == JsonToken.BEGIN_OBJECT) {
                    reader.beginObject();
                    while (reader.hasNext()) {
                        String k = reader.nextName();
                        if ("text".equals(k)) {
                            out.append(reader.nextString()).append(' ');
                        } else {
                            reader.skipValue();
                        }
                    }
                    reader.endObject();
                } else {
                    reader.skipValue();
                }
            }
            reader.endArray();
        } else {
            reader.skipValue();
        }
    }

    // ------------------------------------------------------------------- scan

    private void scanText(String raw, String date, String chatName) {
        String lower = raw.toLowerCase();
        for (Map.Entry<TelegramFlag.Category, String[]> entry : KEYWORDS.entrySet()) {
            TelegramFlag.Category category = entry.getKey();
            int hit = firstHit(lower, entry.getValue());
            if (hit < 0) continue;

            result.rawCounts.merge(category, 1, Integer::sum);

            if (result.flags.size() >= MAX_FLAGS) continue;
            if (perCategory.getOrDefault(category, 0) >= MAX_PER_CATEGORY) continue;
            result.flags.add(new TelegramFlag(category, snippet(raw, hit), parseDate(date), chatName));
            perCategory.merge(category, 1, Integer::sum);
        }
    }

    private static int firstHit(String lower, String[] keywords) {
        int best = -1;
        for (String kw : keywords) {
            int idx = lower.indexOf(kw);
            if (idx >= 0 && (best < 0 || idx < best)) best = idx;
        }
        return best;
    }

    private static String snippet(String raw, int hit) {
        int start = Math.max(0, hit - SNIPPET_BEFORE);
        int end = Math.min(raw.length(), hit + SNIPPET_AFTER);
        String s = raw.substring(start, end).replaceAll("\\s+", " ").trim();
        s = s.replaceAll("\\+?\\d[\\d()\\- ]{9,}\\d", "[номер]"); // redact phone-like sequences
        if (start > 0) s = "…" + s;
        if (end < raw.length()) s = s + "…";
        return s;
    }

    private static LocalDate parseDate(String date) {
        if (date == null || date.length() < 10) return null;
        try {
            return LocalDate.parse(date.substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<TelegramFlag.Category, String[]> buildKeywords() {
        Map<TelegramFlag.Category, String[]> m = new EnumMap<>(TelegramFlag.Category.class);
        m.put(TelegramFlag.Category.WORK_ACTIVITY, new String[]{
                "проект", "дедлайн", "заказчик", "клиент", "задач", "спринт", "релиз",
                "команд", "созвон", "работаю над", "новый контракт", "стажировк",
                "собеседование", "оффер", "подработк", "фриланс", "заказ на", "выполнил заказ",
                "гонорар", "выплат", "смена на работе", "рабочий день"
        });
        m.put(TelegramFlag.Category.FINANCIAL_LITERACY, new String[]{
                "бюджет на месяц", "веду бюджет", "финансовая подушка", "накоплени",
                "откладываю", "инвестир", "вклад под", "депозит", "брокерск", "облигаци",
                "дивиденд", "кэшбэк", "финансовая грамотность", "подписан на канал про финансы",
                "учёт расходов", "планирую расходы"
        });
        m.put(TelegramFlag.Category.OVERDUE, new String[]{
                "просрочк", "просрочил", "просрочила", "задолженност", "коллектор",
                "приставы", "пени за", "штраф за просроч", "нечем платить по кредит",
                "не плачу кредит", "арестовали счёт", "арестовали счет"
        });
        m.put(TelegramFlag.Category.CREDIT_MFO, new String[]{
                "микрозайм", " мфо", "мфо ", "займ под процент", "оформил кредит",
                "оформила кредит", "взял кредит", "взяла кредит", "новый кредит",
                "рассрочк", "кредитная карта", "кредитку", "потребкредит", "рефинанс"
        });
        m.put(TelegramFlag.Category.LOANS_PEOPLE, new String[]{
                "займи", "займу", "занял", "заняла", "одолжи", "одолжу", "в долг",
                "до зарплаты", "скинь до", "перекинь до", "верну на следующей",
                "отдам долг", "должок", "я тебе должен", "я должна тебе"
        });
        m.put(TelegramFlag.Category.STRESS, new String[]{
                "денег нет вообще", "совсем нет денег", "занять негде", "по уши в долгах",
                "долги душат", "кассовый разрыв", "не хватает до зарплаты",
                "живу в минус", "сижу в минусе", "нечего есть"
        });
        return m;
    }

    // exposed for potential tests
    static List<TelegramFlag.Category> categories() {
        return new ArrayList<>(KEYWORDS.keySet());
    }
}
