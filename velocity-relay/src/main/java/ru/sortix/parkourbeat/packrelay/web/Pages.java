package ru.sortix.parkourbeat.packrelay.web;

public final class Pages {
    private Pages() {
    }

    private static final String CSS =
        "body{font-family:Arial,Helvetica,sans-serif;background:#fff;color:#111;"
            + "margin:0;display:flex;align-items:center;justify-content:center;min-height:100vh}"
            + ".box{width:420px;max-width:90%;text-align:center;padding:24px;border:1px solid #ddd}"
            + "h1{font-size:20px;margin:0 0 4px;color:#7B2FBE}"
            + "p{font-size:13px;color:#555;margin:6px 0 18px}"
            + "label{display:block;text-align:left;font-size:12px;color:#555;margin:10px 0 4px}"
            + "input[type=text],input[type=file]{width:100%;box-sizing:border-box;padding:8px;"
            + "border:1px solid #ccc;font-size:13px}"
            + "input[type=text]:focus{outline:none;border-color:#7B2FBE}"
            + "button{margin-top:18px;width:100%;padding:10px;background:#7B2FBE;color:#fff;"
            + "border:0;font-size:14px;cursor:pointer}"
            + "button:hover{background:#5E2295}"
            + ".hint{font-size:11px;color:#888;margin-top:14px}"
            + "a{color:#7B2FBE}"
            + ".steps{text-align:left;font-size:12px;color:#555;line-height:1.5}"
            + ".steps p{margin:10px 0}";

    private static String page(String title, String body) {
        return "<!DOCTYPE html><html lang=\"ru\"><head><meta charset=\"utf-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<title>" + escape(title) + "</title><style>" + CSS + "</style></head>"
            + "<body><div class=\"box\">" + body + "</div></body></html>";
    }

    public static String form(String token, String playerName, int used, int max, long maxBytes) {
        return page("Загрузка трека",
            "<h1>Загрузите сюда трек ogg</h1>"
                + "<p>" + escape(playerName) + " &middot; занято " + used + " из " + max + "</p>"
                + "<form method=\"post\" action=\"/upload/" + escape(token) + "\" enctype=\"multipart/form-data\">"
                + "<label>Файл трека (.ogg, до " + (maxBytes / 1048576L) + " МБ)</label>"
                + "<input type=\"file\" name=\"track\" accept=\".ogg,audio/ogg\" required>"
                + "<label>Автор трека</label>"
                + "<input type=\"text\" name=\"author\" maxlength=\"48\" required>"
                + "<label>Название трека</label>"
                + "<input type=\"text\" name=\"title\" maxlength=\"48\" required>"
                + "<button type=\"submit\">Загрузить</button>"
                + "</form>"
                + "<div class=\"hint\">Ссылка временная и работает только для вас.</div>");
    }

    public static String textureForm(String token, String playerName, String levelName,
                                    String versionRange, long maxBytes) {
        return page("Загрузка текстур",
            "<h1>Загрузите сюда архив .zip</h1>"
                + "<p>" + escape(playerName) + " &middot; уровень " + escape(levelName) + "</p>"
                + "<form method=\"post\" action=\"/textures/" + escape(token) + "\" enctype=\"multipart/form-data\">"
                + "<label>Архив ресурспака (.zip, до " + (maxBytes / 1048576L) + " МБ)</label>"
                + "<input type=\"file\" name=\"pack\" accept=\".zip,application/zip\" required>"
                + "<button type=\"submit\">Загрузить</button>"
                + "</form>"
                + "<div class=\"hint\">Внутри архива обязателен pack.mcmeta.<br>"
                + "Выбранный диапазон версий: " + escape(versionRange) + "</div>");
    }

    public static String message(String title, String description) {
        return page(title,
            "<h1>" + escape(title) + "</h1>"
                + (description == null ? "" : "<p>" + escape(description) + "</p>"));
    }

    public static String messageWithSteps(String title, String description, String[] steps) {
        StringBuilder body = new StringBuilder();
        body.append("<h1>").append(escape(title)).append("</h1>");
        if (description != null) body.append("<p>").append(escape(description)).append("</p>");

        body.append("<div class=\"steps\">");
        for (String step : steps) {
            body.append("<p>").append(linkify(step)).append("</p>");
        }
        body.append("</div>");
        return page(title, body.toString());
    }

    private static String linkify(String text) {
        StringBuilder result = new StringBuilder();
        int index = 0;
        while (index < text.length()) {
            int start = text.indexOf("https://", index);
            if (start < 0) {
                result.append(escape(text.substring(index)));
                break;
            }
            result.append(escape(text.substring(index, start)));

            int end = start;
            while (end < text.length() && !Character.isWhitespace(text.charAt(end))) end++;

            String url = text.substring(start, end);
            result.append("<a href=\"").append(escape(url)).append("\" target=\"_blank\" rel=\"noreferrer\">")
                .append(escape(url)).append("</a>");
            index = end;
        }
        return result.toString();
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}
