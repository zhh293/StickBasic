public class EncodingUtils {
    // 多次URL解码（处理嵌套编码，如%2527 → %27 → '）
    public static String urlDecode(String content) {
        if (content == null)
            return null;
        String decoded = content;
        while (true) {
            String temp = URLDecoder.decode(decoded, StandardCharsets.UTF_8);
            if (temp.equals(decoded))
                break; // 无法再解码时退出
            decoded = temp;
        }
        return decoded;
    }

    // Unicode解码（如\u0027 → '）
    public static String unicodeDecode(String content) {
        if (content == null)
            return null;
        Pattern unicodePattern = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
        Matcher matcher = unicodePattern.matcher(content);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            char c = (char) Integer.parseInt(matcher.group(1), 16);
            matcher.appendReplacement(sb, String.valueOf(c));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    // 合并所有解码步骤
    public static String fullDecode(String content) {
        if (content == null)
            return null;
        String decoded = urlDecode(content);
        decoded = unicodeDecode(decoded);
        return decoded; // 可扩展HTML实体解码等
    }
}
