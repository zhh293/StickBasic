public class XssUtils {
    // 恶意脚本正则（覆盖常见XSS payload）
    private static final Pattern XSS_PATTERN = Pattern.compile(
            "(<script.*?>.*?</script.*?>)|(<.*?on.*?=.*?>)|(javascript:.*?)|(vbscript:.*?)|(data:.*?)|(<.*?href=.*?>)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    // HTML特殊字符转义映射
    private static final Map<Character, String> ESCAPE_MAP = new HashMap<Character, String>() {
        {
            put('<', "&lt;");
            put('>', "&gt;");
            put('&', "&amp;");
            put('"', "&quot;");
            put('\'', "&#39;");
            put('/', "&#47;");
            put('\\', "&#92;");
        }
    };

    /**
     * 全解码（处理嵌套编码，避免绕过）
     */
    public static String fullDecode(String content) {
        if (content == null)
            return null;
        String decoded = content;
        // 1. 多次URL解码（处理%253C → %3C → <）
        decoded = urlDecode(decoded);
        // 2. Unicode解码（处理\u003c → <）
        decoded = unicodeDecode(decoded);
        // 3. HTML实体解码（处理&lt; → <）
        decoded = htmlEntityDecode(decoded);
        return decoded;
    }

    /**
     * 净化内容（根据模式转义或移除恶意内容）
     */
    public static String sanitize(String content, XssProperties.XssMode mode) {
        if (content == null || content.isEmpty())
            return content;

        // 先解码，避免恶意内容被编码隐藏
        String decodedContent = fullDecode(content);

        // 模式1：转义特殊字符（推荐，保留业务数据）
        if (mode == XssProperties.XssMode.ESCAPE) {
            StringBuilder sb = new StringBuilder(decodedContent.length());
            for (char c : decodedContent.toCharArray()) {
                sb.append(ESCAPE_MAP.getOrDefault(c, String.valueOf(c)));
            }
            return sb.toString();
        }

        // 模式2：移除恶意内容（严格模式，可能丢失正常数据）
        if (mode == XssProperties.XssMode.REMOVE) {
            return XSS_PATTERN.matcher(decodedContent).replaceAll("");
        }

        return content;
    }

    // 辅助：URL解码（支持多次解码）
    private static String urlDecode(String content) {
        if (content == null)
            return null;
        String decoded = content;
        while (true) {
            try {
                String temp = URLDecoder.decode(decoded, StandardCharsets.UTF_8.name());
                if (temp.equals(decoded))
                    break;
                decoded = temp;
            } catch (UnsupportedEncodingException e) {
                break;
            }
        }
        return decoded;
    }

    // 辅助：Unicode解码
    private static String unicodeDecode(String content) {
        if (content == null)
            return null;
        Pattern pattern = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
        Matcher matcher = pattern.matcher(content);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, String.valueOf((char) Integer.parseInt(matcher.group(1), 16)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    // 辅助：HTML实体解码
    private static String htmlEntityDecode(String content) {
        if (content == null)
            return null;
        return org.springframework.web.util.HtmlUtils.htmlUnescape(content);
    }

    /**
     * 检测是否包含恶意内容（用于日志告警，不直接拦截，避免误判）
     */
    public static boolean isMalicious(String content) {
        if (content == null)
            return false;
        String decoded = fullDecode(content);
        return XSS_PATTERN.matcher(decoded).find();
    }
}
