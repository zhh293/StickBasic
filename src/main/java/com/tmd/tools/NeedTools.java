package com.tmd.tools;


import org.apache.http.impl.client.CloseableHttpClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;

@Component
public class NeedTools {

    @Tool(name = "获取当前时间")
    public String getTime(){
        return "现在时间是：" + java.time.LocalDateTime.now();
    }

    @Tool(name = "供联网搜索测字相关方法")
    public String getWordInfo(@ToolParam(description = "请输入一个字，切记只能是一个字，不要多传，也不要传各种空白符号") String word){
        // 验证输入是否为单个字符
        if (word == null || word.trim().length() != 1) {
            return "输入错误：请确保只输入一个汉字，不要有多余字符或空格";
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

                return resultSb.toString();
            } else {
                return "请求失败，状态码：" + statusLine.getStatusCode();
            }
        } catch (Exception e) {
            return "请求过程中出现异常：" + e.getMessage();
        }
    }

}
