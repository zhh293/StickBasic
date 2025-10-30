package com.tmd.tools;

import java.io.FileReader;
import java.io.IOException;
import java.util.Scanner;

/**
 * @Description
 * @Author Bluegod
 * @Date 2025/9/10
 */
public class ttttools {
    void ReadFile1() throws IOException {
        String fileName = "C:\\Users\\wwwsh\\Desktop\\BasicBack\\ttt.txt";

        try (Scanner sc = new Scanner(new FileReader(fileName))) {
            while (sc.hasNextLine()) {  //按行读取字符串
                String line = sc.nextLine();
                if(line.isEmpty())continue;
            }
        }
    }
}
