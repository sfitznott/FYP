import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.expression.discrete.relational.ReExpression;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.util.tools.ArrayUtils;

import java.util.Arrays;
import java.util.Random;

public class ConstrainedGraph {
    public int[][] edges;
    public IntVar[][] open;
    public IntVar[][] spc;
    public IntVar[][] tc;
    public IntVar[][] eCost;
    public int numVertices;
    public Model model;
    public IntVar tcSum;
    public int[][] concreteTC;
    public int type;

    public ConstrainedGraph(int size, int type) {
        this.numVertices = size;
        this.model = new Model("g");
        this.edges = new int[size][size];
        this.open = model.intVarMatrix(size,size,0,1);
        this.eCost = model.intVarMatrix(size,size, 1,size);
        this.spc = model.intVarMatrix(size,size,0,size);
        this.tc = model.intVarMatrix(size,size,0,1);
        this.tcSum = this.model.intVar(0, size*size);
        this.model.sum(ArrayUtils.flatten(this.tc), "=", this.tcSum).post();
        this.concreteTC = new int[size][size];
        this.type = type;
    }

    // Add edges
    public void addEdge(int i, int j) {
        edges[i][j] = 1;
    }

    // Remove edges
    public void removeEdge(int i, int j) {
        edges[i][j] = 0;
    }

    /*
    Only call this when the state of the graph is finalised
     */
    public void constrain() {
        //set up constraints
        for (int row=0; row < this.numVertices; row++) {
            for (int col = 0; col < this.numVertices; col++) {
                //eCost cells tied to open
                this.model.ifThenElse(
                        this.model.arithm(this.open[row][col], "=", 0),
                        this.model.arithm(this.eCost[row][col], "=", this.numVertices),
                        this.model.arithm(this.eCost[row][col], "=", 1)
                );

                //tc tied to spc
                this.tc[row][col].eq(1).iff(
                        this.spc[row][col].gt(0).and(
                                this.spc[row][col].lt(this.numVertices))).post();

                //edges constrains open
                if (this.edges[row][col] == 0) {
                    this.model.arithm(this.open[row][col], "=", 0).post();
                }

                //diagonal is 0
                if (row == col) {
                    this.model.arithm(this.spc[row][col], "=", 0).post();
                } else {
                    switch (type) {
                        case 1:
                            //explicit costs recorded and minimised for each node
                                IntVar[] potentialCosts = this.model.intVarArray(this.numVertices, 0, this.numVertices * 2);
                                for (int in = 0; in < this.numVertices; in++) {
                                    this.model.arithm(potentialCosts[in], "=", this.spc[row][in].add(this.eCost[in][col]).intVar()).post();
                                }
                                this.model.min(this.spc[row][col], potentialCosts).post();
                                break;

                        case 2:
                            //path relationships entirely maintained by constraints
                                //nodes with an in-degree of 0 can't be final step
                                if (Arrays.stream(ArrayUtils.getColumn(this.edges, col)).sum() == 0) {
                                    this.model.arithm(this.spc[row][col], "=", this.numVertices).post();
                                } else {
                                    for (int in = 0; in < this.numVertices; in++) {
                                        IntVar totalCost = this.spc[row][in].add(this.eCost[in][col]).intVar();
                                        if (this.edges[in][col] == 1) {
                                            this.model.arithm(this.spc[row][col], "<=", totalCost).post();
                                        }
                                    }

                                    ReExpression[] pathsExist = new ReExpression[this.numVertices];
                                    for (int i=0;i<this.numVertices;i++) {
                                        pathsExist[i] = this.spc[row][col].eq(this.spc[row][i].add(this.eCost[i][col]));
                                    }
                                    this.spc[row][col].eq(this.numVertices).or(pathsExist).post();
                                }
                                break;

                        default:
                            //same as above but with use of the global min constraint
                                //nodes with an in-degree of 0 can't be final step
                                if (Arrays.stream(ArrayUtils.getColumn(this.edges, col)).sum() == 0) {
                                    this.model.arithm(this.spc[row][col], "=", this.numVertices).post();
                                } else {
                                    for (int in = 0; in < this.numVertices; in++) {
                                        IntVar totalCost = this.spc[row][in].add(this.eCost[in][col]).intVar();
                                        if (this.edges[in][col] == 1) {
                                            this.model.arithm(this.spc[row][col], "<=", totalCost).post();
                                        }
                                    }

                                    IntVar[] pathsExist = new IntVar[this.numVertices];
                                    for (int i=0;i<this.numVertices;i++) {
                                        pathsExist[i] = this.spc[row][i].add(this.eCost[i][col]).intVar();
                                    }
                                    IntVar min = this.model.intVar(0, this.numVertices);
                                    this.model.min(min, pathsExist).post();

                                    this.spc[row][col].eq(this.numVertices).or(this.spc[row][col].eq(min)).post();

                                }
                                break;
                    }
                }
            }
        }
    }

    // Print the matrix
    public String toString() {
        StringBuilder s = new StringBuilder("  ");
        for (int i = 0; i < numVertices; i++) {
            s.append(" " + i);

        }
        s.append("\n");
        for (int i = 0; i < numVertices; i++) {
            s.append(i + ": ");
            for (int j : edges[i]) {
                s.append(j + " ");
            }
            s.append("\n");
        }
        return s.toString();
    }

    public void printAll() {
        System.out.println(this.toString());
        System.out.println("Open Edges");
        for(IntVar[] line: this.open) {
            for (int i = 0; i < numVertices; i++) {
                System.out.print(line[i].getValue() + " ");
            }
            System.out.println();
        }

        System.out.println("\nEdge Cost");
        for(IntVar[] line: this.eCost) {
            for (int i = 0; i < numVertices; i++) {
                System.out.print(line[i].getValue() + " ");
            }
            System.out.println();
        }

        System.out.println("\nTransitive Closure");
        for(IntVar[] line: this.tc) {
            for (int i = 0; i < numVertices; i++) {
                if (line[i].getValue() == numVertices || line[i].getValue() == 0) {
                    System.out.print("x" + " ");
                } else {
                    System.out.print(line[i].getValue() + " ");
                }
            }
            System.out.println();
        }

        System.out.println("\nCosts of travel");
        for(IntVar[] line: this.spc) {
            for (int i = 0; i < numVertices; i++) {
                if (line[i].getValue() == numVertices || line[i].getValue() == 0) {
                    System.out.print("x" + " ");
                } else {
                    System.out.print(line[i].getValue() + " ");
                }
            }
            System.out.println();
        }
    }


    public static void main(String[] args) {
        Random r = new Random();
        int size = 4;
        int edgeRatio = 2;
        ConstrainedGraph g = new ConstrainedGraph(size, 1);

        for (int r1=0; r1<size/edgeRatio; r1++) {
            for (int r2=0; r2<size/edgeRatio; r2++) {
                int i = r.nextInt(size);
                int j = r.nextInt(size);
                while (i == j) {
                    i = r.nextInt(size);
                    j = r.nextInt(size);
                }
                g.addEdge(i, j);
            }
        }

        g.constrain();
        Solver solver = g.model.getSolver();
        g.model.setObjective(Model.MAXIMIZE, g.tcSum);
        while (solver.solve()) {
            for(int i = 0; i< g.numVertices; i++) {
                for (int j = 0; j < g.numVertices; j++) {
                    g.concreteTC[i][j]=g.tc[i][j].getValue();
                }
            }
            g.printAll();
        }
        solver.printStatistics();
    }

}
