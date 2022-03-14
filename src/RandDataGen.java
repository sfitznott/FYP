import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solution;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.search.limits.FailCounter;
import org.chocosolver.solver.search.loop.lns.INeighborFactory;
import org.chocosolver.solver.search.measure.MeasuresRecorder;
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
    private static int timelimit = 130;

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
    public static void genCycles(int type, int size,  int cycles, int pos, int neg) throws IOException {
        double resTime = 0.0;
        double bestTime = 0.0;
        double buildTime = 0.0;
        double unoptimalTTB = 0.0;
        int optimal = 0;
        int unoptimal = 0;
        int fails = 0;
        FileWriter w = new FileWriter(type+"_"+100+"6_50.txt", true);
        String rep = "";

        rep += ("\n-----------------------\nSize: "+size+
                " type: "+type+
                " pos: "+pos+
                " neg: "+neg);
        for (int j=0; j<cycles; j++) {
            if (fails > 2000) {
                break;
            }
            ConstrainedGraph g = genGraph(type, size);
            System.out.println("Graph "+j+" generated");
            rep+=("\n"+((Arrays.stream(ArrayUtils.flatten(g.edges)).sum())/2) + " edges");

            GraphGenerator.genBaseReachability(g);
            /*
            for(int[] line: g.concreteTC) {
                for (int i = 0; i < size; i++) {
                    System.out.print(line[i] + " ");
                }
                System.out.println();
            }*/
            GraphGenerator.genReachability(g, pos, neg);

            Solver solver = g.model.getSolver();
            MeasuresRecorder measures = solver.getMeasures();

            solver.setSearch(activityBasedSearch(ArrayUtils.flatten(g.open)));
            //solver.setSearch(activityBasedSearch(g.model.retrieveIntVars(false)));
            solver.limitTime(timelimit);
            //solver.setLNS(INeighborFactory.random(ArrayUtils.flatten(g.open)), new FailCounter(g.model, 100));
            //solver.setLNS(INeighborFactory.random(g.model.retrieveIntVars(false)), new FailCounter(g.model, 100));
            //Solution s = solver.findOptimalSolution(g.tcSum, Model.MAXIMIZE);
            g.model.setObjective(Model.MAXIMIZE, g.tcSum);
            Solution s = new Solution(g.model);
            while (solver.solve()) {
                s.record();
                rep+=("\n"+s.getIntVal(g.tcSum) +" at "+ measures.getTimeCount());

            }
            if (s.exists()) {
                /*
                System.out.println(g);/*
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
                if (measures.getTimeCount() > timelimit) {
                    unoptimal += 1;
                    unoptimalTTB += measures.getTimeToBestSolution();
                    //rep+=("\nGraph " + j + " stopped at " + measures.getTimeCount() + measures.getReadingTimeCount());
                } else {
                    optimal += 1;
                    resTime += measures.getTimeCount();
                    buildTime += measures.getReadingTimeCount();
                    bestTime += measures.getTimeToBestSolution();
                    //rep+=("\nGraph " + j + " solved in " + measures.getTimeCount() + measures.getReadingTimeCount()+"\n");
                    System.out.println("Graph " + j + " solved in " + measures.getTimeCount() + measures.getReadingTimeCount()+"\n");
                }
            }
            else {
                j-=1;
                fails += 1;
                rep+=("\nSolving failed\nfails: "+fails);
            }
            solver.hardReset();
        }
        String res = "\n--------------------\nOptimal solves: " + optimal +
                "\navg build time: " + (buildTime / cycles) +
                "\navg resTime: " + (resTime / cycles - unoptimal) +
                "\navg best time: " + (bestTime / cycles) +
                "\nUnoptimal solves: " + unoptimal +
                "\navg best time: " + (unoptimalTTB / max(cycles - optimal, 1)) +
                "\nfails: " + fails + "\n";

        System.out.println(res);
        w.write(rep+res);
        w.close();
    }

    public static void main(String[] args) throws IOException {
        for (int type=1;type<=3;type++) {
            File record = new File(type+"_"+100+"6_50.txt");
            System.out.println("type :"+type);
            for (int size=6;size<=50;size+=2) {
                if (size<20) {
                    RandDataGen.genCycles(type, size, 100, 1, 1);
                } else {
                    RandDataGen.genCycles(type, size, 100, (size/10)-1, (size/10)-1);
                }
            }
        }
    }
}
