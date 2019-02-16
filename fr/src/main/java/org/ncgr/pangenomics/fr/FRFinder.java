package org.ncgr.pangenomics.fr;

import org.ncgr.pangenomics.Graph;
import org.ncgr.pangenomics.Node;
import org.ncgr.pangenomics.NodeSet;
import org.ncgr.pangenomics.Path;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;

import java.util.Collections;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.concurrent.PriorityBlockingQueue;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * Finds frequented regions in a Graph.
 *
 * See Cleary, et al., "Exploring Frequented Regions in Pan-Genomic Graphs", IEEE/ACM Trans Comput Biol Bioinform. 2018 Aug 9. PMID:30106690 DOI:10.1109/TCBB.2018.2864564
 *
 * @author Sam Hokin
 */
public class FRFinder {

    // optional parameter defaults
    static int MINSUP = 1;
    static int MAXSUP = Integer.MAX_VALUE;
    static int MINSIZE = 1;
    static int MINLEN = 1;
    static boolean CASE_CTRL = false;
    static boolean USERC = false;
    static boolean VERBOSE = false;
    static boolean DEBUG = false;
    
    // required parameters, no defaults; set in constructor
    Graph graph;  // the Graph we're analyzing
    double alpha; // penetrance: the fraction of a supporting strain's sequence that actually supports the FR; alternatively, `1-alpha` is the fraction of inserted sequence
    int kappa;    // maximum insertion: the maximum insertion length (measured in bp) that any supporting path may have
 
    // optional parameters, set with setters
    boolean verbose = VERBOSE;
    boolean debug = DEBUG;
    boolean useRC = USERC; // indicates if the sequence (e.g. FASTA file) had its reverse complement appended
    int minSup = MINSUP;   // minimum support: minimum number of genome paths (fr.support) for an FR to be considered interesting
    int maxSup = MAXSUP;   // maximum support: maximum number of genome paths (fr.support) for an FR to be considered interesting
    int minSize = MINSIZE; // minimum size: minimum number of de Bruijn nodes (fr.nodes.size()) that an FR must contain to be considered interesting
    int minLen = MINLEN;   // minimum average length of a frequented region's subpath sequences (fr.avgLength) to be considered interesting
    boolean caseCtrl = CASE_CTRL; // emphasize FRs that have large case/control support
    String outputPrefix = null; // output file for FRs (stdout if null)

    // the FRs, sorted for convenience
    TreeSet<FrequentedRegion> frequentedRegions;

    // utility items for post-processing
    String inputFile;
    String outputHeading;
    Map<NodeSet,String> outputLines;

    /**
     * Construct with a populated Graph and required parameters
     */
    public FRFinder(Graph graph, double alpha, int kappa) {
        this.graph = graph;
        this.alpha = alpha;
        this.kappa = kappa;
    }

    /**
     * Construct with the output from a previous run
     */
    public FRFinder(String inputFile) throws FileNotFoundException, IOException {
        this.inputFile = inputFile;
        readFrequentedRegions(inputFile);
    }

    /**
     * Find the frequented regions in this Graph.
     */
    public void findFRs() throws IOException {

        if (verbose) {
            graph.printNodes();
            graph.printPaths();
            graph.printNodePaths();
        }

        // store the saved FRs in a TreeSet
        frequentedRegions = new TreeSet<>();
        
        // store the studied FRs in a synchronizedSet
        Set<FrequentedRegion> syncFrequentedRegions = Collections.synchronizedSet(new TreeSet<>());
        
        // create initial single-node FRs
        for (Node node : graph.nodes.values()) {
            NodeSet c = new NodeSet();
            c.add(node);
            Set<Path> s = new HashSet<>();
            for (Path p : graph.paths) {
                Set<Path> support = FrequentedRegion.computeSupport(c, p, alpha, kappa);
                s.addAll(support);
            }
            if (s.size()>0) {
                FrequentedRegion fr = new FrequentedRegion(graph, c, s, alpha, kappa);
                syncFrequentedRegions.add(fr);
            }
        }

        // keep track of FRs we've already looked at
        List<FrequentedRegion> usedFRs = new LinkedList<>();
        
        // build the FRs round by round
        int round = 0;
        boolean added = true;
        while (added) {
            round++;
            added = false;

            // gently suggest garbage collection
            System.gc();

            // put FR pairs into a PriorityBlockingQueue which sorts them by decreasing interest (defined by the FRPair comparator)
            PriorityBlockingQueue<FRPair> pbq = new PriorityBlockingQueue<>();

            ////////////////////////////////////////
            // spin through FRs in a parallel manner
            syncFrequentedRegions.parallelStream().forEach((fr1) -> {
                    syncFrequentedRegions.parallelStream().forEach((fr2) -> {
                            if (!usedFRs.contains(fr1) && !usedFRs.contains(fr2) && fr2.compareTo(fr1)>0) {
                                pbq.add(new FRPair(fr1,fr2));
                            }
                        });
                });
            ////////////////////////////////////////
            
            // add our new FR
            if (pbq.size()>0) {
                FRPair frpair = pbq.peek();
                if (frpair.merged.support>0) {
                    added = true;
                    usedFRs.add(frpair.fr1);
                    usedFRs.add(frpair.fr2);
                    syncFrequentedRegions.add(frpair.merged);
                    // flag FRs that don't meet the filters
                    boolean passes = true;
                    String reason = "";
                    if (frpair.merged.support<minSup) {
                        passes = false;
                        reason += " support";
                    }
                    if (frpair.merged.avgLength<minLen) {
                        passes = false;
                        reason += " avgLength";
                    }
                    if (frpair.merged.nodes.size()<minSize) {
                        passes = false;
                        reason += " size";
                    }
                    if (passes) {
                        reason += " *";
                        frequentedRegions.add(frpair.merged);
                    }
                    System.out.println(round+":"+frpair.merged.toString()+reason);
                }
            }

        }

        System.out.println("Found "+frequentedRegions.size()+" FRs.");

	// final output
        printFrequentedRegions();
        printPathFRs();
        printPathFRsSVM();
    }

    public double getAlpha() {
        return alpha;
    }
    public int getKappa() {
        return kappa;
    }
    public boolean getUseRC() {
        return useRC;
    }
    public int getMinSup() {
        return minSup;
    }
    public int getMaxSup() {
        return maxSup;
    }
    public int getMinSize() {
        return minSize;
    }
    public int getMinLen() {
        return minLen;
    }

    // setters for optional parameters
    public void setVerbose() {
        this.verbose = true;
    }
    public void setDebug() {
        this.debug = true;
    }
    public void setMinSup(int minSup) {
        this.minSup = minSup;
    }
    public void setMaxSup(int maxSup) {
        this.maxSup = maxSup;
    }
    public void setMinSize(int minSize) {
        this.minSize = minSize;
    }
    public void setMinLen(int minLen) {
        this.minLen = minLen;
    }
    public void setCaseCtrl() {
        this.caseCtrl = true;
    }
    public void setUseRC() {
        this.useRC = true;
    }
    public void setOutputPrefix(String outputPrefix) {
        this.outputPrefix = outputPrefix;
    }

    /**
     * Command-line utility
     */
    public static void main(String[] args) throws FileNotFoundException, IOException {

        Options options = new Options();
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        //
        Option alphaOption = new Option("a", "alpha", true, "alpha=penetrance, fraction of a supporting path's sequence that supports the FR (required)");
        alphaOption.setRequired(true);
        options.addOption(alphaOption);
	// 
        Option dotOption = new Option("d", "dot", true, "splitMEM DOT file (requires FASTA file)");
        dotOption.setRequired(false);
        options.addOption(dotOption);
        //
        Option fastaOption = new Option("f", "fasta", true, "FASTA file (requires DOT file)");
        fastaOption.setRequired(false);
        options.addOption(fastaOption);
        //
        Option genotypeOption = new Option("g", "genotype", true, "which genotype to include (0,1) from the input file; -1 to include all ("+Graph.GENOTYPE+")");
        genotypeOption.setRequired(false);
        options.addOption(genotypeOption);
        //
        Option jsonOption = new Option("j", "json", true, "vg JSON file");
        jsonOption.setRequired(false);
        options.addOption(jsonOption);
        //
        Option gfaOption = new Option("gfa", "gfa", true, "GFA file");
        gfaOption.setRequired(false);
        options.addOption(gfaOption);
        //
        Option kappaOption = new Option("k", "kappa", true, "kappa=maximum insertion length that any supporting path may have (required)");
        kappaOption.setRequired(true);
        options.addOption(kappaOption);
        //
        Option minLenOption = new Option("l", "minlen", true, "minlen=minimum allowed average length (bp) of an FR's subpaths ("+MINLEN+")");
        minLenOption.setRequired(false);
        options.addOption(minLenOption);
        //
        Option minSupOption = new Option("m", "minsup", true, "minsup=minimum number of supporting paths for a region to be considered interesting ("+MINSUP+")");
        minSupOption.setRequired(false);
        options.addOption(minSupOption);
        //
        Option maxSupOption = new Option("n", "maxsup", true, "maxsup=maximum number of supporting paths for a region to be considered interesting ("+MAXSUP+")");
        maxSupOption.setRequired(false);
        options.addOption(maxSupOption);
        //
        Option outputprefixOption = new Option("o", "outputprefix", true, "output file prefix (stdout)");
        outputprefixOption.setRequired(false);
        options.addOption(outputprefixOption);
        //
        Option labelsOption = new Option("p", "pathlabels", true, "tab-delimited file with pathname<tab>label");
        labelsOption.setRequired(false);
        options.addOption(labelsOption);
        //
        Option rcOption = new Option("r", "userc", false, "useRC=flag to indicate if the sequence (e.g. FASTA) had its reverse complement appended ("+USERC+")");
        rcOption.setRequired(false);
        options.addOption(rcOption);
        //
        Option minSizeOption = new Option("s", "minsize", true, "minsize=minimum number of nodes that a FR must contain to be considered interesting ("+MINSIZE+")");
        minSizeOption.setRequired(false);
        options.addOption(minSizeOption);
        //
        Option verboseOption = new Option("v", "verbose", false, "verbose output ("+VERBOSE+")");
        verboseOption.setRequired(false);
        options.addOption(verboseOption);
        //
        Option debugOption = new Option("do", "debug", false, "debug output ("+DEBUG+")");
        debugOption.setRequired(false);
        options.addOption(debugOption);
        //
        Option graphOnlyOption = new Option("go", "graphonly", false, "just read the graph and output, do not find FRs; for debuggery (false)");
        graphOnlyOption.setRequired(false);
        options.addOption(graphOnlyOption);
        //
        Option caseCtrlOption = new Option("cc", "casectrl", false, "emphasize FRs that have large case vs. control support ("+CASE_CTRL+")");
        caseCtrlOption.setRequired(false);
        options.addOption(caseCtrlOption);

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            formatter.printHelp("FRFinder", options);
            System.exit(1);
            return;
        }

        // parameter validation
        if (!cmd.hasOption("dot") && !cmd.hasOption("json") && !cmd.hasOption("gfa")) {
            System.err.println("You must specify a splitMEM-style DOT file plus FASTA (-d/--dot and -f/--fasta ), a vg JSON file (-j, --json) or a vg GFA file (--gfa)");
            System.exit(1);
            return;
        }
        if (cmd.hasOption("dot") && !cmd.hasOption("fasta")) {
            System.err.println("If you specify a splitMEM dot file (-d/--dot) you MUST ALSO specify a FASTA file (-f/--fasta)");
            System.exit(1);
            return;
        }
        
        // files
        String dotFile = cmd.getOptionValue("dot");
        String fastaFile = cmd.getOptionValue("fasta");
        String jsonFile = cmd.getOptionValue("json");
        String gfaFile = cmd.getOptionValue("gfa");
        String pathLabelsFile = cmd.getOptionValue("pathlabels");

        // required parameters
        double alpha = Double.parseDouble(cmd.getOptionValue("alpha"));
        int kappa = Integer.parseInt(cmd.getOptionValue("kappa"));

        // create a Graph from the dot+FASTA or JSON or GFA file
        Graph g = new Graph();
        if (cmd.hasOption("verbose")) g.setVerbose();
        if (cmd.hasOption("debug")) g.setDebug();
        if (cmd.hasOption("genotype")) g.genotype = Integer.parseInt(cmd.getOptionValue("genotype"));
        if (dotFile!=null && fastaFile!=null) {
            g.readSplitMEMDotFile(dotFile, fastaFile);
        } else if (jsonFile!=null) {
            g.readVgJsonFile(jsonFile);
        } else if (gfaFile!=null) {
            g.readVgGfaFile(gfaFile);
        } else {
            System.err.println("ERROR: no DOT+FASTA or JSON or GFA provided.");
            System.exit(1);
        }

        // if a labels file is given, append the labels to the path names
        if (pathLabelsFile!=null) {
            g.readPathLabels(pathLabelsFile);
        }

        // bail if we're just looking at the graph
        if (cmd.hasOption("graphonly")) {
            if (pathLabelsFile!=null) g.printLabelCounts();
            g.printNodes();
            g.printPaths();
            g.printNodePaths();
            g.printPathSequences();
            return;
        }
        
        // instantiate the FRFinder with this Graph and required parameters
        FRFinder frf = new FRFinder(g, alpha, kappa);
        
        // set optional FRFinder parameters
        if (cmd.hasOption("verbose")) frf.setVerbose();
        if (cmd.hasOption("debug")) frf.setDebug();
        if (cmd.hasOption("userc")) frf.setUseRC();
        if (cmd.hasOption("minsup")) {
            frf.setMinSup(Integer.parseInt(cmd.getOptionValue("minsup")));
        }
        if (cmd.hasOption("maxsup")) {
            frf.setMaxSup(Integer.parseInt(cmd.getOptionValue("maxsup")));
        }
        if (cmd.hasOption("minsize")) {
            frf.setMinSize(Integer.parseInt(cmd.getOptionValue("minsize")));
        }
        if (cmd.hasOption("minlen")) {
            frf.setMinLen(Integer.parseInt(cmd.getOptionValue("minlen")));
        }
        if (cmd.hasOption("casectrl")) {
            frf.setCaseCtrl();
        }
        if (cmd.hasOption("outputprefix")) {
            frf.setOutputPrefix(cmd.getOptionValue("outputprefix"));
        }

        // print out the parameters to stdout or outputPrefix+".params" if exists
        frf.printParameters();
        
        //////////////////
        // Find the FRs //
        //////////////////
        frf.findFRs();
    }

    /**
     * Contains a pair of FRs for storage in the PriorityBlockingQueue with a comparator to decide which FRs "win".
     * This is where one implements weighting for certain FR characteristics.
     */
    class FRPair implements Comparable<FRPair> {
        FrequentedRegion fr1;
        FrequentedRegion fr2;
        FrequentedRegion merged;
        FRPair(FrequentedRegion fr1, FrequentedRegion fr2) {
            this.fr1 = fr1;
            this.fr2 = fr2;
            merged = FrequentedRegion.merge(fr1, fr2, graph, alpha, kappa);
        }
        public int compareTo(FRPair that) {
            if (caseCtrl) {
                // use distance from case=control line if different
                int thisDistance = Math.abs(this.merged.getLabelCount("case")-this.merged.getLabelCount("ctrl"));
                int thatDistance = Math.abs(that.merged.getLabelCount("case")-that.merged.getLabelCount("ctrl"));
                if (thisDistance!=thatDistance) {
                    return Integer.compare(thatDistance, thisDistance);
                }
            }
            // default: support then avgLength then size
            if (that.merged.support!=this.merged.support) {
                return Integer.compare(that.merged.support, this.merged.support);
            } else if (that.merged.avgLength!=this.merged.avgLength) {
                return Double.compare(that.merged.avgLength, this.merged.avgLength);
            } else {
                return Integer.compare(that.merged.nodes.size(), this.merged.nodes.size());
            }
        }
    }

    /**
     * Print a delineating heading, for general use.
     */
    static void printHeading(String heading) {
        for (int i=0; i<heading.length(); i++) System.out.print("="); System.out.println("");
        System.out.println(heading);
        for (int i=0; i<heading.length(); i++) System.out.print("="); System.out.println("");
    }

    /**
     * Print the path names and the count of subpaths for each FR, to stdout or outputPrefix.paths.txt.
     * This can be used as input to a classification routine.
     */
    void printPathFRs() throws IOException {
        PrintStream out = null;
        // output from a findFRs run
        if (outputPrefix==null) {
            out = System.out;
            printHeading("PATH FREQUENTED REGIONS");
        } else {
            out = new PrintStream(outputPrefix+".paths.txt");
        }
        // columns
        boolean first = true;
        for (Path path : graph.paths) {
            if (first) {
                first = false;
            } else {
                out.print("\t");
            }
            out.print(path.getNameAndLabel());
        }
        out.println("");        
        // rows
        int c = 1;
        for (FrequentedRegion fr : frequentedRegions) {
            out.print(c++);
            for (Path path : graph.paths) {
                out.print("\t"+fr.countSubpathsOf(path));
            }
            out.println("");
        }
        if (outputPrefix!=null) out.close();
    }

    /**
     * Print the path FR support for libsvm. Lines are like:
     * +1 1:1 2:1 3:1 4:0 ...
     * -1 1:0 2:0 3:0 4:1 ...
     * +1 1:0 2:1 3:0 4:2 ...
     * Where +1 corresponds to "case" and -1 corresponds to "ctrl" (0 otherwise).
     */
    void printPathFRsSVM() throws IOException {
        PrintStream out = null;
        // output from a findFRs run
        if (outputPrefix==null) {
            out = System.out;
            printHeading("PATH SVM RECORDS");
        } else {
            out = new PrintStream(outputPrefix+".svm.txt");
        }
        // only rows, one per path
        for (Path path : graph.paths) {
            String group = "0";
            if (path.label!=null && path.label.equals("case")) {
                group = "+1";
            } else if (path.label!=null && path.label.equals("ctrl")) {
                group = "-1";
            }
            out.print(group);
            int c = 0;
            for (FrequentedRegion fr : frequentedRegions) {
                c++;
                out.print("\t"+c+":"+fr.countSubpathsOf(path));
            }
            out.println("");
        }
        if (outputPrefix!=null) out.close();
    }
    
    /**
     * Print out the FRs, either to stdout or outputPrefix.frs.txt
     */
    void printFrequentedRegions() throws IOException {
        if (frequentedRegions.size()==0) {
            System.err.println("NO FREQUENTED REGIONS!");
            return;
        }
        PrintStream out = null;
        if (inputFile==null) {
            // output from a findFRs run
            if (outputPrefix==null) {
                out = System.out;
                printHeading("FREQUENTED REGIONS");
            } else {
                out = new PrintStream(outputPrefix+".frs.txt");
            }
            out.println(frequentedRegions.first().columnHeading());
            for (FrequentedRegion fr : frequentedRegions) {
                out.println(fr.toString());
            }
        } else {
            // output from post-processing
            if (outputPrefix==null) {
                out = System.out;
                printHeading("FREQUENTED REGIONS");
            } else {
                out = new PrintStream(outputPrefix+".frs.txt");
            }
            out.println(outputHeading);
            for (FrequentedRegion fr : frequentedRegions) {
                out.println(outputLines.get(fr.nodes));
            }
        }
        if (outputPrefix!=null) out.close();
    }

    /**
     * Read FR NodeSets from the output from a previous run.
     * 0   1           2       3      4        5        6        7        ...
     * FR  nodes       support avgLen label1.n label1.f label2.n label2.f ...
     * 1   [5,7,15,33] 28      282    18       0.667    10       1.000    ...
     */
    void readFrequentedRegions(String inputFile) throws FileNotFoundException, IOException {
        BufferedReader reader = new BufferedReader(new FileReader(inputFile));
        frequentedRegions = new TreeSet<>();
        outputLines = new TreeMap<>();
        outputHeading = reader.readLine();
        String line = null;
        while ((line=reader.readLine())!=null) {
            String[] fields = line.split("\t");
            String nsString = fields[1];
            NodeSet ns = new NodeSet(nsString);
            outputLines.put(ns,line);
            FrequentedRegion fr = new FrequentedRegion(ns);
            frequentedRegions.add(fr);
        }
    }

    /**
     * Print a crude histogram of FR node sizes.
     */
    public void printFRHistogram() {
        Map<Integer,Integer> countMap = new TreeMap<>();
        int maxSize = 0;
        for (FrequentedRegion fr : frequentedRegions) {
            if (fr.nodes.size()>maxSize) maxSize = fr.nodes.size();
            if (countMap.containsKey(fr.nodes.size())) {
                int count = countMap.get(fr.nodes.size());
                count++;
                countMap.put(fr.nodes.size(), count);
            } else {
                countMap.put(fr.nodes.size(), 1);
            }
        }
        for (int num : countMap.keySet()) {
            System.out.println("FR node size (#):"+num+" ("+countMap.get(num)+")");
        }
    }

    /**
     * Print out the parameters, either to stdout or outputPrefix.params.txt
     */
    public void printParameters() throws IOException {
        PrintStream out = null;
        if (outputPrefix==null) {
            out = System.out;
            printHeading("PARAMETERS");
        } else {
            // no heading for file output; append to output file
            String paramFile = outputPrefix+".params.txt";
            out = new PrintStream(paramFile);
        }
        // Graph
        if (graph!=null) {
            out.println("genotype"+"\t"+graph.genotype);
            if (graph.jsonFile!=null) out.println("jsonfile"+"\t"+graph.jsonFile);
            if (graph.gfaFile!=null) out.println("gfafile"+"\t"+graph.gfaFile);
            if (graph.dotFile!=null) out.println("dotfile"+"\t"+graph.dotFile);
            if (graph.fastaFile!=null) out.println("fastafile"+"\t"+graph.fastaFile);
        }
        // FRFinder
        out.println("alpha"+"\t"+alpha);
        out.println("kappa"+"\t"+kappa);
        out.println("minsup"+"\t"+minSup);
        out.println("maxsup"+"\t"+maxSup);
        out.println("minsize"+"\t"+minSize);
        out.println("minlen"+"\t"+minLen);
        out.println("casectrl"+"\t"+caseCtrl);
        out.println("userc"+"\t"+useRC);
        if (inputFile!=null) out.println("inputfile"+"\t"+inputFile);
        if (outputPrefix!=null) out.println("outputprefix"+"\t"+outputPrefix);
    }

    /**
     * Read in the parameters from a previous run, presuming inputFile is not null.
     */
    void readParameters() throws FileNotFoundException, IOException {
        if (inputFile==null) return;
        String paramFile = inputFile+".params";
        BufferedReader reader = new BufferedReader(new FileReader(paramFile));
        String line = null;
        String jsonFile = null;
        String dotFile = null;
        String fastaFile = null;
        int genotype = Graph.GENOTYPE;
        while ((line=reader.readLine())!=null) {
            String[] parts = line.split("\t");
            if (parts[0].equals("jsonfile")) {
                jsonFile = parts[1];
            } else if (parts[0].equals("dotFile")) {
                dotFile = parts[1];
            } else if (parts[0].equals("fastafile")) {
                fastaFile = parts[1];
            } else if (parts[0].equals("genotype")) {
                genotype = Integer.parseInt(parts[1]);
            } else if (parts[0].equals("alpha")) {
                alpha = Double.parseDouble(parts[1]);
            } else if (parts[0].equals("kappa")) {
                kappa = Integer.parseInt(parts[1]);
            } else if (parts[0].equals("minsup")) {
                minSup = Integer.parseInt(parts[1]);
            } else if (parts[0].equals("maxsup")) {
                maxSup = Integer.parseInt(parts[1]);
            } else if (parts[0].equals("minsize")) {
                minSize = Integer.parseInt(parts[1]);
            } else if (parts[0].equals("minlen")) {
                minLen = Integer.parseInt(parts[1]);
            } else if (parts[0].equals("userc")) {
                useRC = Boolean.parseBoolean(parts[1]);
            }
            // load the Graph if we've got the files
            if (jsonFile!=null) {
                graph = new Graph();
                graph.genotype = genotype;
                graph.readVgJsonFile(jsonFile);
            } else if (dotFile!=null && fastaFile!=null) {
                graph = new Graph();
                graph.genotype = genotype;
                graph.readSplitMEMDotFile(dotFile, fastaFile);
            }
        }
    }
}
