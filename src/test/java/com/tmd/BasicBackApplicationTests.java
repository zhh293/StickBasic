package com.tmd;

import org.checkerframework.checker.units.qual.A;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.PriorityQueue;


class BasicBackApplicationTests {
    /**
     * 图的顶点数量
     */
    private int vertices;

    /**
     * 邻接矩阵表示图
     */
    private int[][] adjacencyMatrix;

    /**
     * 构造函数
     * @param vertices 顶点数
     */
    public BasicBackApplicationTests(int vertices) {
        this.vertices = vertices;
        this.adjacencyMatrix = new int[vertices][vertices];

        // 初始化邻接矩阵，用Integer.MAX_VALUE表示无连接
        for (int i = 0; i < vertices; i++) {
            for (int j = 0; j < vertices; j++) {
                if (i == j) {
                    adjacencyMatrix[i][j] = 0;
                } else {
                    adjacencyMatrix[ i][j] = Integer.MAX_VALUE;
                }
            }
        }
    }

    /**
     * 添加边到图中
     * @param source 起始顶点
     * @param destination 目标顶点
     * @param weight 边的权重
     */
    public void addEdge(int source, int destination, int weight) {
        if (source >= 0 && source < vertices &&
                destination >= 0 && destination < vertices) {
            adjacencyMatrix[source][destination] = weight;
        }
    }


    public int[] dijkstra(int source) {
        int[]distances = new int[vertices];
        boolean[] visited = new boolean[vertices];
        Arrays.fill(distances, Integer.MAX_VALUE);
        distances[source] = 0;
        Arrays.fill(visited, false);
        PriorityQueue<Vertex> queue = new PriorityQueue<>(vertices, (a, b) -> a.distance - b.distance);
        queue.add(new Vertex(source, 0));
        while (!queue.isEmpty()) {
            //取出当前距离原点最小的点
            Vertex current = queue.poll();
            if(visited[current.index]) continue;
            visited[current.index] = true;
            for(int i=0;i<vertices;i++){
                if(adjacencyMatrix[current.index][i] != Integer.MAX_VALUE && !visited[i]){
                    int newDistance = distances[current.index] + adjacencyMatrix[current.index][i];
                    if(newDistance<distances[i]){
                        distances[i] = newDistance;
                        queue.add(new Vertex(i, newDistance));
                    }
                }
            }
        }
        return distances;
    }
    class Vertex{
        int index;
        int distance;
        public Vertex(int index, int distance){
            this.index = index;
            this.distance = distance;
        }
    }

    public static void main(String[] args) {
        BasicBackApplicationTests graph = new BasicBackApplicationTests(5);
        graph.addEdge(0, 1, 2);
        graph.addEdge(0, 2, 4);
        graph.addEdge(1, 2, 1);
        graph.addEdge(1, 3, 7);
        graph.addEdge(2, 3, 3);
        graph.addEdge(2, 4, 1);
        int[] dijkstra = graph.dijkstra(0);
        for(int i=0;i<dijkstra.length;i++){
            System.out.println("从0到"+i+"的最短距离为："+dijkstra[i]);
        }
    }
}
