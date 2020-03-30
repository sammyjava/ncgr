package org.ncgr.svm;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;

import java.text.DecimalFormat;

import java.util.Formatter;
import java.util.StringTokenizer;
import java.util.List;
import java.util.ArrayList;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * Utility to scale data to a given interval like [-1,1].
 */
public class SvmScaler {
    
    // defaults
    double lower = -1.0;
    double upper = 1.0;
    double yLower;
    double yUpper;
    boolean yScaling = false;

    // file I/O
    // BufferedReader fpRestore;

    double[] featureMax;
    double[] featureMin;
    double yMax = +1; // case=+1
    double yMin = -1; // ctrl=-1
    long numNonzeroes = 0;
    long newNumNonzeroes = 0;
    int max_index = 0;

    // our samples
    List<Sample> samples;

    /**
     * Construct given a data file.
     */
    public SvmScaler(String dataFilename) throws FileNotFoundException, IOException {
        this.samples = SvmUtil.readSamples(dataFilename);
    }

    /**
     * Construct given a List of samples.
     */
    public SvmScaler(List<Sample> samples) {
        this.samples = samples;
    }

    /**
     * Pass 1: find out max index of attributes from the restore file.
     * Assumption: min index of attributes is 1.
     */
    void findMaxIndex() {
        max_index = 0;
        numNonzeroes = 0;
        // // restore file
        // int idx;
        // int c;
        // int index;
        // if (fpRestore!=null) {
        //     if ((c = fpRestore.read()) == 'y') {
        //         fpRestore.readLine();
        //         fpRestore.readLine();
        //         fpRestore.readLine();
        //     }
        //     fpRestore.readLine();
        //     fpRestore.readLine();
        //     String restore_line = null;
        //     while ((restore_line=fpRestore.readLine())!=null) {
        //         StringTokenizer st2 = new StringTokenizer(restore_line);
        //         idx = Integer.parseInt(st2.nextToken());
        //         max_index = Math.max(max_index, idx);
        //     }
        //     fpRestore = rewind(fpRestore, restoreFilename);
        // }
        // samples data
        for (Sample sample : samples) {
            for (int i : sample.values.keySet()) {
                max_index = Math.max(max_index, i);
                numNonzeroes++;
            }
        }
    }

    /**
     * Pass 2: find out min/max value of each feature.
     */
    void findMinMaxValues() throws IOException {
        featureMax = new double[max_index];
        featureMin = new double[max_index];
        for (int i=0; i<max_index; i++) {
            featureMax[i] = -Double.MAX_VALUE;
            featureMin[i] =  Double.MAX_VALUE;
        }
        for (Sample sample : samples) {
            for (int index : sample.values.keySet()) {
                double value = sample.values.get(index);
                int i = index - 1;
                featureMax[i] = Math.max(featureMax[i], value);
                featureMin[i] = Math.min(featureMin[i], value);
            }
        }
    }

    /**
     * Pass 2.5: save/restore featureMin/featureMax from restore file.
     */
    // void restoreMinMaxValuesFromFiles() throws IOException {
    //     int idx, c;
    //     double fmin, fmax;
    //     fpRestore.mark(2);				// for reset
    //     if ((c = fpRestore.read()) == 'y') {
    //         fpRestore.readLine();		// pass the '\n' after 'y'
    //         StringTokenizer st = new StringTokenizer(fpRestore.readLine());
    //         yLower = Double.parseDouble(st.nextToken());
    //         yUpper = Double.parseDouble(st.nextToken());
    //         st = new StringTokenizer(fpRestore.readLine());
    //         yMin = Double.parseDouble(st.nextToken());
    //         yMax = Double.parseDouble(st.nextToken());
    //         yScaling = true;
    //     } else {
    //         fpRestore.reset();
    //     }
    //     if (fpRestore.read() == 'x') {
    //         fpRestore.readLine();		// pass the '\n' after 'x'
    //         StringTokenizer st = new StringTokenizer(fpRestore.readLine());
    //         lower = Double.parseDouble(st.nextToken());
    //         upper = Double.parseDouble(st.nextToken());
    //         String restore_line = null;
    //         while ((restore_line = fpRestore.readLine())!=null) {
    //             StringTokenizer st2 = new StringTokenizer(restore_line);
    //             idx = Integer.parseInt(st2.nextToken());
    //             fmin = Double.parseDouble(st2.nextToken());
    //             fmax = Double.parseDouble(st2.nextToken());
    //             if (idx<max_index) {
    //                 featureMin[idx] = fmin;
    //                 featureMax[idx] = fmax;
    //             }
    //         }
    //     }
    // }

    /**
     * Pass 3: scale to an output file.
     * TODO: just scale to new list of samples; move output to another method.
     */
    void scaleToOutput(PrintStream out) throws IOException {
        for (Sample sample : samples) {
            out.print(sample.name+"\t"+sample.label);
            int next_index = 1;
            for (int index : sample.values.keySet()) {
                for (int i=next_index; i<index; i++) output(out, i, 0); // ???
                output(out, index, sample.values.get(index));
                next_index = index + 1;
            }
            for (int i=next_index; i<=max_index; i++) output(out, i, 0); // ???
            out.println("");
        }
        // newNumNonzeroes is incremented in output()
        if (newNumNonzeroes > numNonzeroes) {
            System.err.println("WARNING: original #nonzeros " + numNonzeroes);
            System.err.println("         new      #nonzeros " + newNumNonzeroes);
            System.err.println("Use -l 0 if many original feature values are zeros");
        }
    }
    
    /**
     * Run the scaling job from/to files.
     */
    void run() throws IOException {
        // if (restoreFilename!=null) ...
        findMaxIndex();
        findMinMaxValues();
        // if (fpRestore!=null) {
        //     restoreMinMaxValuesFromFiles();
        // }
        // scale to stdout
        scaleToOutput(System.out);
        // close the files
        // if (fpRestore!=null) {
        //     fpRestore.close();
        // }
    }

    /**
     * Write to the save file.
     */
    void writeSaveFile(String saveFilename) throws IOException {
        Formatter formatter = new Formatter(new StringBuilder());
        BufferedWriter bw = null;
        bw = new BufferedWriter(new FileWriter(saveFilename));
        if (yScaling) {
            formatter.format("y\n");
            formatter.format("%.16g %.16g\n", yLower, yUpper);
            formatter.format("%.16g %.16g\n", yMin, yMax);
        }
        formatter.format("x\n");
        formatter.format("%.16g %.16g\n", lower, upper);
        for (int i=0; i<max_index; i++) {
            if (featureMin[i]!=featureMax[i]) formatter.format("%d %.16g %.16g\n", i, featureMin[i], featureMax[i]);
        }
        bw.write(formatter.toString());
        bw.close();
    }

    /**
     * Command-line version, loads data from files.
     */
    public static void main(String[] args) throws IOException {

        Options options = new Options();
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        Option lowerOption = new Option("l", true, "x scaling lower limit [-1]");
        lowerOption.setRequired(false);
        options.addOption(lowerOption);

        Option upperOption = new Option("u", true, "x scaling upper limit [+1]");
        upperOption.setRequired(false);
        options.addOption(upperOption);

        Option yScalingOption = new Option("y", true, "y scaling limits yLower,yUpper [no y scaling]");
        yScalingOption.setRequired(false);
        options.addOption(yScalingOption);

        Option saveFileOption = new Option("s", true, "filename to save scaling parameters to");
        saveFileOption.setRequired(false);
        options.addOption(saveFileOption);

        // Option restoreFileOption = new Option("r", true, "filename to read scaling parameters from");
        // restoreFileOption.setRequired(false);
        // options.addOption(restoreFileOption);

        if (args.length==0) {
            formatter.printHelp("SvmScaler", options);
            System.exit(1);
        }
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            formatter.printHelp("SvmScaler", options);
            System.exit(1);
            return;
        }

        // file names
        String dataFilename = args[args.length-1];  // data file is required and last argument
        // String restoreFilename = null;
        String saveFilename = null;
        if (cmd.hasOption("s")) {
            saveFilename = cmd.getOptionValue("s");
        }
        // if (cmd.hasOption("r")) {
        //     restoreFilename = cmd.getOptionValue("r");
        // }

        // instantiate default instance from data file
        SvmScaler scaler = new SvmScaler(dataFilename);
        
        // update with options
        if (cmd.hasOption("l")) {
            scaler.lower = Double.parseDouble(cmd.getOptionValue("l"));
        }
        if (cmd.hasOption("u")) {
            scaler.upper = Double.parseDouble(cmd.getOptionValue("u"));
        }
        if (cmd.hasOption("y")) {
            String[] parts = cmd.getOptionValue("y").split(",");
            scaler.yLower = Double.parseDouble(parts[0]);
            scaler.yUpper = Double.parseDouble(parts[1]);
            scaler.yScaling = true;
        }

        // validate
        scaler.validateParameters();
        // validateFiles(dataFilename, saveFilename);
    
        // run scaling
        scaler.run();

        // write save file
        if (saveFilename!=null) {
            scaler.writeSaveFile(saveFilename);
        }
    }

    /**
     * Validate files.
     */
    void validateFiles(String dataFilename, String saveFilename) {
        if (dataFilename==null) {
            System.err.println("You have not provided a data file to scale.");
            System.exit(1);
        }
        // if (saveFilename!=null && restoreFilename!=null) {
        //     System.err.println("The -s and -r options are mutually exclusive: you may save scaling parameters XOR restore parameters for application to data.");
        //     System.exit(1);
        // }
    }

    /**
     * Validate parameters
     */
    void validateParameters() {
        if (!(upper>lower)) {
            System.err.println("You have provided inconsistent lower/upper values.");
            System.exit(1);
        }
        if (yScaling && !(yUpper>yLower)) {
            System.err.println("You have provided inconsistent y-scaling values.");
            System.exit(1);
        }
    }

    /**
     * Dorky.
     */
    void output(PrintStream out, int index, double value) {
        int i = index - 1;
        // skip single-valued attribute
        if (featureMax[i]==featureMin[i]) return;
        if (value==featureMin[i]) {
            value = lower;
        } else if (value==featureMax[i]) {
            value = upper;
        } else {
            value = lower + (upper-lower) *  (value-featureMin[i])/(featureMax[i]-featureMin[i]);
        }
        if (value!=0) {
            out.print("\t"+index+":"+value);
            newNumNonzeroes++;
        }
    }
}