import java.util.Random;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solver;
import org.graphstream.algorithm.generator.BarabasiAlbertGenerator;
import org.graphstream.algorithm.generator.DorogovtsevMendesGenerator;
import org.graphstream.algorithm.generator.Generator;
import org.graphstream.algorithm.generator.WattsStrogatzGenerator;
import org.graphstream.graph.Graph;
import org.graphstream.graph.implementations.SingleGraph;

public class GraphGenerator {
    public static int type = 1;
    private static Random r = new Random();

    public static ConstrainedGraph randomGraph(int size) {
        int rand = 0;
        while (rand <= 0) rand = r.nextInt(size/2);
        return randomGraph(size, rand);
    }
    /*
    Generates a graph of 'size' nodes with up to size*edgeRatio-r.nextInt(size) edges with no other rulings
     */
    public static ConstrainedGraph randomGraph(int size, int edgeRatio){
        ConstrainedGraph g = new ConstrainedGraph(size, type);
        for (int r1=0; r1<(size*edgeRatio)-r.nextInt(size-edgeRatio); r1++) {
                int i = r.nextInt(size);
                int j = r.nextInt(size);
                while (i == j) {
                    i = r.nextInt(size);
                    j = r.nextInt(size);
                }
                g.addEdge(i, j);
        }
        g.constrain();
        return g;
    }

    public static ConstrainedGraph preferential(int size){
        int rand = r.nextInt(size);
        while (rand <= 0) rand = r.nextInt(size);
        return preferential(size, rand);
    }

    /*
      This is a very simple graph generator that generates a graph using the preferential attachment rule defined in the
      Barabási-Albert model: nodes are generated one by one, and each time attached by one or more edges other nodes.
      The other nodes are chosen using a biased random selection giving more chance to a node if it has a high degree.
    */
    public static ConstrainedGraph preferential(int size, int maxLinksPerStep) {
        Graph graph = new SingleGraph("Barabàsi-Albert");
        // Between 1 and 3 new links per node added.
        Generator gen = new BarabasiAlbertGenerator(maxLinksPerStep);
        // Generate nodes:
        gen.addSink(graph);
        gen.begin();
        for (int i=0; i<size-2; i++) {
            gen.nextEvents();
        }
        gen.end();
        return graphToConstrained(graph, type);
    }

    public static ConstrainedGraph planar(int size) {
        Graph graph = new SingleGraph("Dorogovtsev mendes");
        Generator gen = new DorogovtsevMendesGenerator();
        gen.addSink(graph);
        gen.begin();
        for(int i=0; i<size-3; i++) {
            gen.nextEvents();
        }

        gen.end();
        return graphToConstrained(graph, type);
    }

    public static ConstrainedGraph smallWorld(int size){
        int rand = r.nextInt(size);
        while (rand <= 2 || rand % 2 != 0) rand = r.nextInt(size);
        return smallWorld(size, rand, r.nextDouble());
    }

    public static ConstrainedGraph smallWorld(int size, int k, double beta) {
        Graph graph = new SingleGraph(".");
        Generator gen = new WattsStrogatzGenerator(size, k, beta);
        gen.addSink(graph);
        gen.begin();
        while(gen.nextEvents()) {}
        gen.end();
        return graphToConstrained(graph, type);
    }

    /*
    Returns ConstrainedGraph with same edges as graph provided
     */
    private static ConstrainedGraph graphToConstrained(Graph graph, int type) {
        int size = graph.getNodeCount();
        ConstrainedGraph cg = new ConstrainedGraph(size,type);
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                cg.edges[i][j] = graph.getNode(i).hasEdgeBetween(j) ? 1 : 0;
            }
        }
        cg.constrain();
        return cg;
    }

    public static void genBaseReachability(ConstrainedGraph g) {
        Solver solver = g.model.getSolver();
        g.model.setObjective(Model.MAXIMIZE, g.tcSum);
        while (solver.solve()) {
            for(int i = 0; i< g.numVertices; i++) {
                for (int j = 0; j < g.numVertices; j++) {
                    g.concreteTC[i][j]=g.tc[i][j].getValue();
                }
            }
        }
        solver.hardReset();
    }

    public static void genReachability(ConstrainedGraph g) {
        genReachability(g, r.nextInt(g.numVertices/2), r.nextInt(g.numVertices/2));
    }

    public static void genReachability(ConstrainedGraph g, int pos, int neg) {
        int size = g.numVertices;
        while (neg > 0) {
            int i = r.nextInt(size);
            int j = r.nextInt(size);
            if (g.concreteTC[i][j] == 1) {
                g.concreteTC[i][j]=0;
                g.model.arithm(g.tc[i][j], "=", 0).post();
                neg-=1;
                //System.out.println(" neg: " + i + " -> " + j);
            }
        }

        while (pos > 0) {
            int i = r.nextInt(size);
            int j = r.nextInt(size);
            if (g.concreteTC[i][j] == 1) {
                g.model.arithm(g.tc[i][j], "=", 1).post();
                pos-=1;
                //System.out.println(" pos: " + i + " -> " + j);
            }
        }
    }

}
