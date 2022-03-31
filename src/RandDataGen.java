import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solution;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.search.limits.FailCounter;
import org.chocosolver.solver.search.loop.lns.INeighborFactory;
import org.chocosolver.solver.search.measure.MeasuresRecorder;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.util.tools.ArrayUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import static java.lang.Math.max;
import static org.chocosolver.solver.search.strategy.Search.activityBasedSearch;

public class RandDataGen {
    private static Random rand = new Random();
    private static int timelimit = 120;

    /*
    type:
        0 for random
        1 for preferential
        2 for planar
        3 for small world
    size is number of nodes
     */
    public static ConstrainedGraph genGraph(int type, int size) {
        return switch (type) {
            case 1 -> GraphGenerator.preferential(size);
            case 2 -> GraphGenerator.planar(size);
            case 3 -> GraphGenerator.smallWorld(size);
            default -> GraphGenerator.randomGraph(size);
        };
    }


    /*
    size must be greater than 4 in order for small world generation to work
     */
    public static void genCycles(int type, int size,  int cycles, int pos, int neg, String file1, String file2) throws IOException {
        double[] resTime = new double[] {0.0,0.0};
        double[] bestTime = new double[] {0.0,0.0};
        double[] buildTime = new double[] {0.0,0.0};
        double[] unoptimalTTB = new double[] {0.0,0.0};
        int[] optimal = new int[] {0,0};
        int[] unoptimal = new int[] {0,0};
        int[] fails = new int[] {0,0};
        int edgecount = 0;
        int edgetotal = 0;

        FileWriter w = new FileWriter(file1, true);
        FileWriter wLNS = new FileWriter(file2, true);

        String rep = ("\n-----------------------\nSize: "+size+
                " type: "+type+
                " pos: "+pos+
                " neg: "+neg);
        String repLNS = rep+ " LNS";

        for (int j=0; j<cycles; j++) {
            if (fails[0] == 2*j+1) {
                break;
            }

            ConstrainedGraph g = genGraph(type, size);
            System.out.println("Graph "+j+" generated");
            System.out.println(g);
            edgecount = ((Arrays.stream(ArrayUtils.flatten(g.edges)).sum())/2);
            edgetotal+=edgecount;
            System.out.println(""+edgecount+ " edges");

            GraphGenerator.genBaseReachability(g);
            GraphGenerator.genReachability(g, pos, neg);

            Solver solver = g.model.getSolver();
            MeasuresRecorder measures = solver.getMeasures();
            solver.limitTime(""+timelimit+"s");

            //activity run
            System.out.println("\nStandard with Activity search");
            solver.setSearch(activityBasedSearch(ArrayUtils.flatten(g.open)));
            g.model.setObjective(Model.MAXIMIZE, g.tcSum);

            Solution s = new Solution(g.model);
            while (solver.solve()) {
                s.record();
                System.out.println(s.getIntVal(g.tcSum) +" at "+ measures.getTimeCount());

            }
            if (s.exists()) {
                /*
                System.out.println(g);
                System.out.println("Open Edges");
                for(IntVar[] line: g.open) {
                    for (int i = 0; i < size; i++) {
                        System.out.print(s.getIntVal(line[i]) + " ");
                    }
                    System.out.println();
                }

                System.out.println("\nEdge Cost");
                for(IntVar[] line: g.eCost) {
                    for (int i = 0; i < size; i++) {
                        System.out.print(s.getIntVal(line[i]) + " ");
                    }
                    System.out.println();
                }

                System.out.println("\nTransitive Closure");
                for(IntVar[] line: g.tc) {
                    for (int i = 0; i < size; i++) {
                        if (s.getIntVal(line[i]) == size || s.getIntVal(line[i]) == 0) {
                            System.out.print("x" + " ");
                        } else {
                            System.out.print(s.getIntVal(line[i]) + " ");
                        }
                    }
                    System.out.println();
                }

                System.out.println("\nCosts of travel");
                for(IntVar[] line: g.spc) {
                    for (int i = 0; i < size; i++) {
                        if (s.getIntVal(line[i]) == size || s.getIntVal(line[i])== 0) {
                            System.out.print("x" + " ");
                        } else {
                            System.out.print(s.getIntVal(line[i]) + " ");
                        }
                    }
                    System.out.println();
                }
                    solver.printStatistics();*/
                    if (!measures.isObjectiveOptimal()) {
                        unoptimal[0] += 1;
                        unoptimalTTB[0] += measures.getTimeToBestSolution();
                        System.out.println("Graph " + j + " stopped at " + measures.getTimeCount() + measures.getReadingTimeCount());
                    } else {
                        optimal[0] += 1;
                        resTime[0] += measures.getTimeCount();
                        buildTime[0] += measures.getReadingTimeCount();
                        bestTime[0] += measures.getTimeToBestSolution();
                        //rep+=("\nGraph " + j + " solved in " + measures.getTimeCount() + measures.getReadingTimeCount()+"\n");
                        System.out.println("Graph " + j + " solved in " + measures.getTimeCount() + measures.getReadingTimeCount());
                    }
            }
            else {
                j-=1;
                fails[0] += 1;
                edgetotal-=edgecount;
            }

            solver.hardReset();

            //LNS RUN
            System.out.println("\nLNS with Activity search");
            int[][] edgeTemp = g.edges;
            g = new ConstrainedGraph(size, type);
            g.edges= edgeTemp;
            g.constrain();

            GraphGenerator.genBaseReachability(g);
            GraphGenerator.genReachability(g, pos, neg);

            solver = g.model.getSolver();
            measures = solver.getMeasures();
            solver.limitTime(""+timelimit+"s");

            solver.setSearch(activityBasedSearch(ArrayUtils.flatten(g.open)));
            solver.setLNS(INeighborFactory.random(ArrayUtils.flatten(g.open)), new FailCounter(g.model, 100));
            g.model.setObjective(Model.MAXIMIZE, g.tcSum);

            Solution sLNS = new Solution(g.model);
            while (solver.solve()) {
                sLNS.record();
                System.out.println(sLNS.getIntVal(g.tcSum) +" at "+ measures.getTimeCount());

            }
            if (sLNS.exists()) {
/*
                System.out.println(g);
            System.out.println("Open Edges");
            for(IntVar[] line: g.open) {
                for (int i = 0; i < size; i++) {
                    System.out.print(sLNS.getIntVal(line[i]) + " ");
                }
                System.out.println();
            }

            System.out.println("\nEdge Cost");
            for(IntVar[] line: g.eCost) {
                for (int i = 0; i < size; i++) {
                    System.out.print(sLNS.getIntVal(line[i]) + " ");
                }
                System.out.println();
            }

            System.out.println("\nTransitive Closure");
            for(IntVar[] line: g.tc) {
                for (int i = 0; i < size; i++) {
                    if (sLNS.getIntVal(line[i]) == size || sLNS.getIntVal(line[i]) == 0) {
                        System.out.print("x" + " ");
                    } else {
                        System.out.print(sLNS.getIntVal(line[i]) + " ");
                    }
                }
                System.out.println();
            }

            System.out.println("\nCosts of travel");
            for(IntVar[] line: g.spc) {
                for (int i = 0; i < size; i++) {
                    if (sLNS.getIntVal(line[i]) == size || sLNS.getIntVal(line[i])== 0) {
                        System.out.print("x" + " ");
                    } else {
                        System.out.print(sLNS.getIntVal(line[i]) + " ");
                    }
                }
                System.out.println();
            }
                solver.printStatistics();*/
                if (!measures.isObjectiveOptimal()) {
                    unoptimal[1] += 1;
                    unoptimalTTB[1] += measures.getTimeToBestSolution();
                    System.out.println("Graph " + j + " stopped at " + measures.getTimeCount() + measures.getReadingTimeCount());
                } else {
                    optimal[1] += 1;
                    resTime[1] += measures.getTimeCount();
                    buildTime[1] += measures.getReadingTimeCount();
                    bestTime[1] += measures.getTimeToBestSolution();
                    System.out.println("Graph " + j + " solved in " + measures.getTimeCount() + measures.getReadingTimeCount());
                }
            }
            else {
                j-=1;
                fails[1] += 1;
                edgetotal-=edgecount;
            }
            solver.hardReset();

        }


        String res = "\n--------------------\nOptimal solves: " + optimal[0] +
                "\navg build time: " + (buildTime[0] / cycles) +
                "\navg resTime: " + (resTime[0] / (cycles - unoptimal[0])) +
                "\navg best time: " + (bestTime[0] / cycles) +
                "\navg edges : "+(edgetotal/cycles)+
                "\nUnoptimal solves: " + unoptimal[0] +
                "\navg best time: " + (unoptimalTTB[0] / max(cycles - optimal[0], 1)) +
                "\nfails: " + fails[0] + "\n";

        String resLNS = "\n--------------------\nOptimal solves: " + optimal[1] +
                "\navg build time: " + (buildTime[1] / cycles) +
                "\navg resTime: " + (resTime[1] / (cycles - unoptimal[1])) +
                "\navg best time: " + (bestTime[1] / cycles) +
                "\navg edges : "+(edgetotal/cycles)+
                "\nUnoptimal solves: " + unoptimal[1] +
                "\navg best time: " + (unoptimalTTB[1] / max(cycles - optimal[1], 1)) +
                "\nfails: " + fails[1] + "\n";

        System.out.println(res+"\n\n"+resLNS);
        //w.write(rep+res);
        w.close();
    }

    public static void main(String[] args) throws IOException {
        for (int type=1;type<=1;type++) {
            int cycles = 1;
            String lns = "lns";
            String f1 = type+"_"+100+"_5_50.txt";
            String f2 = type+"_"+100+"_5_50_lns.txt";
            File file1 = new File(f1);
            File file2 = new File(f2);
            System.out.println("type :"+type);
            for (int size=5;size<=5;size+=5) {
                if (size<20) {
                    RandDataGen.genCycles(type, size, cycles, 1, 1, f1, f2);
                } else {
                    RandDataGen.genCycles(type, size, cycles, (size/10)-1, (size/10)-1, f1, f2);
                }
            }
        }
    }
}
