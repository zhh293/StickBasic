package com.tmd;

import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.ToolParam;

//import java.util.Arrays;
//import java.util.PriorityQueue;


class BasicBackApplicationTests {
    @Test
    public void getWordInfo(){
        String word="男";
        if (word == null || word.trim().length() != 1) {
            System.out.println("输入错误：请确保只输入一个汉字，不要有多余字符或空格");
        }

        String url = "https://www.yiyunpan.cn/cezi/result.php";
        try (CloseableHttpClient httpClient = org.apache.http.impl.client.HttpClients.createDefault()) {
            // 创建POST请求
            org.apache.http.client.methods.HttpPost httpPost = new org.apache.http.client.methods.HttpPost(url);

            // 设置请求参数
            java.util.List<org.apache.http.NameValuePair> params = new java.util.ArrayList<>();
            params.add(new org.apache.http.message.BasicNameValuePair("time", String.valueOf(System.currentTimeMillis() / 1000)));
            params.add(new org.apache.http.message.BasicNameValuePair("str", word));

            // 设置请求实体
            httpPost.setEntity(new org.apache.http.client.entity.UrlEncodedFormEntity(params, "UTF-8"));

            // 设置请求头
            httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded");
            httpPost.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            httpPost.setHeader("Referer", "https://www.yiyunpan.cn/cezi/");

            // 执行请求并获取响应
            org.apache.http.HttpResponse response = httpClient.execute(httpPost);
            org.apache.http.StatusLine statusLine = response.getStatusLine();

            if (statusLine.getStatusCode() == 200) {
                // 读取响应内容
                java.io.InputStream inputStream = response.getEntity().getContent();
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(inputStream, "UTF-8"));
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line).append("\n");
                }
                reader.close();
                String html = result.toString();

                // ========== 核心：提取测字结果 ==========
                StringBuilder resultSb = new StringBuilder();
                resultSb.append("【").append(word).append("字测字结果】\n\n");

                // 1. 提取三才解析
                String sanCaiFlag = "<span class=\"dzt_title_c w_3\">三才解析</span>";
                String zhuGeFlag = "<span class=\"dzt_title_c w_3\">诸葛神数</span>";
                if (html.contains(sanCaiFlag)) {
                    // 截取三才解析部分（从三才解析开始，到诸葛神数结束）
                    int sanCaiStart = html.indexOf(sanCaiFlag) + sanCaiFlag.length();
                    int sanCaiEnd = html.indexOf(zhuGeFlag);
                    String sanCaiContent = html.substring(sanCaiStart, sanCaiEnd);

                    // 提取三才解析下的各个维度
                    String[] dimensions = {
                            "总论", "性格", "意志", "事业", "家庭",
                            "婚姻", "子女", "社交", "精神", "财运", "健康", "老运"
                    };
                    resultSb.append("=== 三才解析 ===\n");
                    for (String dim : dimensions) {
                        String dimFlag = "<font color='#FF0000'>" + dim + "：</font>";
                        if (sanCaiContent.contains(dimFlag)) {
                            int dimStart = sanCaiContent.indexOf(dimFlag) + dimFlag.length();
                            // 截取到下一个<font color='#FF0000'>之前（或换行/标签结束）
                            int dimEnd = sanCaiContent.indexOf("<font color='#FF0000'>", dimStart);
                            if (dimEnd == -1) {
                                dimEnd = sanCaiContent.indexOf("<div class=\"line\" style=\"height:20px;\"></div>", dimStart);
                            }
                            // 清理HTML标签、多余空格和换行
                            String dimValue = sanCaiContent.substring(dimStart, dimEnd)
                                    .replaceAll("<br />", "\n　　")
                                    .replaceAll("<.*?>", "")
                                    .replaceAll("\\s+", " ")
                                    .trim();
                            resultSb.append(dim).append("：\n　　").append(dimValue).append("\n\n");
                        } else {
                            resultSb.append(dim).append("：未查询到相关信息\n\n");
                        }
                    }
                } else {
                    resultSb.append("=== 三才解析 ===\n未查询到相关信息\n\n");
                }

                // 2. 提取诸葛神数
                if (html.contains(zhuGeFlag)) {
                    int zhuGeStart = html.indexOf(zhuGeFlag) + zhuGeFlag.length();
                    int zhuGeEnd = html.indexOf("<div id=\"code\" style=", zhuGeStart);
                    String zhuGeContent = html.substring(zhuGeStart, zhuGeEnd);

                    // 提取诗曰
                    String shiYueFlag = "<div class=\"info title\">诗曰</div>";
                    String jieXiFlag = "<div class=\"info title\">解析</div>";
                    if (zhuGeContent.contains(shiYueFlag) && zhuGeContent.contains(jieXiFlag)) {
                        // 提取诗曰内容
                        int shiYueStart = zhuGeContent.indexOf(shiYueFlag) + shiYueFlag.length();
                        int shiYueEnd = zhuGeContent.indexOf(jieXiFlag);
                        String shiYueValue = zhuGeContent.substring(shiYueStart, shiYueEnd)
                                .replaceAll("<div class=\"info\">", "")
                                .replaceAll("</div>", "")
                                .replaceAll("\\s+", " ")
                                .trim()
                                .replace("  ", "\n");

                        // 提取解析内容
                        int jieXiStart = zhuGeContent.indexOf(jieXiFlag) + jieXiFlag.length();
                        String jieXiValue = zhuGeContent.substring(jieXiStart)
                                .replaceAll("<div class=\"info\" style=\"text-align:justify; line-height:150%;\">", "")
                                .replaceAll("</div>", "")
                                .replaceAll("<br />", "\n　　")
                                .replaceAll("\\s+", " ")
                                .trim();

                        resultSb.append("=== 诸葛神数 ===\n");
                        resultSb.append("诗曰：\n　　").append(shiYueValue).append("\n\n");
                        resultSb.append("解析：\n　　").append(jieXiValue).append("\n");
                    } else {
                        resultSb.append("=== 诸葛神数 ===\n未查询到相关信息\n");
                    }
                } else {
                    resultSb.append("=== 诸葛神数 ===\n未查询到相关信息\n");
                }

                System.out.println(resultSb.toString());
            } else {
                System.out.println("请求失败，状态码：" + statusLine.getStatusCode());
            }
        } catch (Exception e) {
            System.out.println("请求过程中出现异常：" + e.getMessage());
        }
    }
//    /**
//     * 图的顶点数量
//     */
//    private int vertices;
//
//    /**
//     * 邻接矩阵表示图
//     */
//    private int[][] adjacencyMatrix;
//
//    /**
//     * 构造函数
//     * @param vertices 顶点数
//     */
//    public BasicBackApplicationTests(int vertices) {
//        this.vertices = vertices;
//        this.adjacencyMatrix = new int[vertices][vertices];
//
//        // 初始化邻接矩阵，用Integer.MAX_VALUE表示无连接
//        for (int i = 0; i < vertices; i++) {
//            for (int j = 0; j < vertices; j++) {
//                if (i == j) {
//                    adjacencyMatrix[i][j] = 0;
//                } else {
//                    adjacencyMatrix[ i][j] = Integer.MAX_VALUE;
//                }
//            }
//        }
//    }
//
//    /**
//     * 添加边到图中
//     * @param source 起始顶点
//     * @param destination 目标顶点
//     * @param weight 边的权重
//     */
//    public void addEdge(int source, int destination, int weight) {
//        if (source >= 0 && source < vertices &&
//                destination >= 0 && destination < vertices) {
//            adjacencyMatrix[source][destination] = weight;
//        }
//    }
//
//
//    public int[] dijkstra(int source) {
//        int[]distances = new int[vertices];
//        boolean[] visited = new boolean[vertices];
//        Arrays.fill(distances, Integer.MAX_VALUE);
//        distances[source] = 0;
//        Arrays.fill(visited, false);
//        PriorityQueue<Vertex> queue = new PriorityQueue<>(vertices, (a, b) -> a.distance - b.distance);
//        queue.add(new Vertex(source, 0));
//        while (!queue.isEmpty()) {
//            //取出当前距离原点最小的点
//            Vertex current = queue.poll();
//            if(visited[current.index]) continue;
//            visited[current.index] = true;
//            for(int i=0;i<vertices;i++){
//                if(adjacencyMatrix[current.index][i] != Integer.MAX_VALUE && !visited[i]){
//                    int newDistance = distances[current.index] + adjacencyMatrix[current.index][i];
//                    if(newDistance<distances[i]){
//                        distances[i] = newDistance;
//                        queue.add(new Vertex(i, newDistance));
//                    }
//                }
//            }
//        }
//        return distances;
//    }
//    class Vertex{
//        int index;
//        int distance;
//        public Vertex(int index, int distance){
//            this.index = index;
//            this.distance = distance;
//        }
//    }
//
//    public static void main(String[] args) {
//        BasicBackApplicationTests graph = new BasicBackApplicationTests(5);
//        graph.addEdge(0, 1, 2);
//        graph.addEdge(0, 2, 4);
//        graph.addEdge(1, 2, 1);
//        graph.addEdge(1, 3, 7);
//        graph.addEdge(2, 3, 3);
//        graph.addEdge(2, 4, 1);
//        int[] dijkstra = graph.dijkstra(0);
//        for(int i=0;i<dijkstra.length;i++){
//            System.out.println("从0到"+i+"的最短距离为："+dijkstra[i]);
//        }
//    }
}
