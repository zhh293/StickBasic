@Component
@ConfigurationProperties(prefix = "app.xss")
@Data
public class XssProperties {
    /**
     * 是否启用XSS防护
     */
    private boolean enabled = true;

    /**
     * 防护模式：ESCAPE（转义，推荐）、REMOVE（移除恶意内容）
     */
    private XssMode mode = XssMode.ESCAPE;

    /**
     * 需要排除的路径（Ant风格，如/static/**、/api/v1/rich-text/**）
     */
    private List<String> excludePaths = new ArrayList<>();

    /**
     * 需要排除的参数名（如富文本字段content、html）
     */
    private List<String> excludeParams = new ArrayList<>();

    /**
     * 需要检测的请求头（默认检测User-Agent、Referer、Cookie）
     */
    private List<String> includeHeaders = Arrays.asList("User-Agent", "Referer", "Cookie");

    /**
     * 最大扫描长度（避免超大请求体导致性能问题，默认1MB）
     */
    private int maxScanLength = 1024 * 1024;

    public enum XssMode {
        ESCAPE, REMOVE
    }
}
