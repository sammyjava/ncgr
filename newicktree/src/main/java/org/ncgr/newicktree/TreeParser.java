package org.ncgr.newicktree;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.SequenceInputStream;
import java.io.StreamTokenizer;

import java.util.Collections;
import java.util.List;
import java.util.LinkedList;
import java.util.EmptyStackException;
import java.util.Stack;
import java.util.Vector;

/**
 * @author James
 * @author Sam Hokin
 *
 * Parses the newick portion of a file.
 * For nexus files, additional node-number mapping is needed to rename files.
 * Identification of a file as either newick or nexus determines contents.
 * 
 * */
public class TreeParser {
    // Nexus file identifier.  We look for this as the first token to identify a tree file as Nexus, or other.
    private static final String nexusFileID = "#NEXUS";
    // Begin tag.
    private static final String beginTag = "begin";
    // End tag.
    private static final String endTag = "end";
    //  trees section
    private static final String treeSectionTag = "trees";
    // Tree ID.
    private static final String treeID = "tree";
    // Tree ID (same or similar to {@link #treeID}?).
    private static final String utreeID = "utree"; // two different tree IDs?
    
    // Line (and tree information) termination.
    private static final char lineTerminator = ';';
    // Equality sign.
    private static final char equals = '=';
    // Nexus comment open.
    private static final char commentOpen = '[';
    // Nexus comment close.
    private static final char commentClose = ']';

    // true: show debug output.  false: suppress printing.
    private static boolean debugOutput = false;
    private StreamTokenizer tokenizer;
    // Root node of the tree being parsed.  Must be initialized outside the tokenizer.
    private TreeNode rootNode;

    /**
     * Initializes parsing of a tree by creating a tokenizer and setting default
     * properties (such as spacing, quoting characters).  
     * {@link #tokenize(long, String)} is required to start the parsing.
     * @param b Buffered reader that could start in the middle of a nexus file or
     * the start of a newick file (basically the beginning of a newick tree, is run
     * for each tree in a nexus file)
     */
    public TreeParser(BufferedReader b) {
        tokenizer = new StreamTokenizer(b);
        tokenizer.eolIsSignificant(false);
        tokenizer.quoteChar('"');
        //        tokenizer.quoteChar('\''); // TODO: check quote layering, quoted quotes
        tokenizer.wordChars('\'', '\''); // quote problem, turn this into a prime symbol?
        // 32 = space
        tokenizer.wordChars('!', '!'); // 33
        // 34 = "
        tokenizer.wordChars('#', '&'); // 35-38
        // 39-41 = '() newick
        tokenizer.wordChars('*', '+'); // 42-43
        // 44 = , newick
        tokenizer.wordChars('-', '/'); // 45-47
        // 48-59 = [0-9]:;
        tokenizer.wordChars('<', '<'); // 60
        // 61 = = nexus
        tokenizer.wordChars('>', '@'); // 62-64
        // 65-90 = [A-Z]
        //        tokenizer.wordChars('[', '['); // 91 [ nexus comment character, treat as char
        // 92 = \ (esc, support esc'd spaces)
        //      93 = ] nexus comment character
        tokenizer.wordChars('^', '`'); // 93-96
        // 97-122 = [a-z]
        tokenizer.wordChars('{', '~'); // 123-126
        // 127 = del
    }
    
    /**
     * Guess the type of treeFile based on the presence of nexus identifiers.
     * @param fileName The name of the file.
     * @return true when file is nexus format, false if nexus strings weren't found.
     */
    public boolean isNexusFile(String fileName) {
        boolean returnValue = false;
        BufferedReader r;
        try {
            r = new BufferedReader(new FileReader(fileName));
            String line = r.readLine();
            if (line.indexOf(nexusFileID) != -1)
                returnValue = true;
            r.close();
        } catch (FileNotFoundException e) {
            System.err.println("Could not find file to identify: " + fileName);
        } catch (IOException e) {
            System.err.println("Couldn't identify file: " + fileName);
        }
        return returnValue;
    }
    
    /**
     * Parses names of trees in nexus file.
     * @param fileName Name of nexus file.
     * @return List of all tree names found in nexus file
     */
    public static LinkedList nexusFileTreeNames(String fileName) {
        LinkedList returnList = null;
        BufferedReader r;
        try {
            r = new BufferedReader(new FileReader(fileName));
            StreamTokenizer st = new StreamTokenizer(r);
            st.wordChars('#', '#');
            st.nextToken();
            returnList = new LinkedList();
            while (st.ttype != StreamTokenizer.TT_EOF) {
                if (st.ttype == StreamTokenizer.TT_WORD) {
                    if (st.sval.equalsIgnoreCase(beginTag)) {
                        st.nextToken();
                        if (st.ttype == StreamTokenizer.TT_WORD &&
                            st.sval.equalsIgnoreCase(treeSectionTag)) {
                            // found a tree section, huzzah
                            boolean endOfTreeList = false;
                            st.nextToken();
                            while (st.ttype != StreamTokenizer.TT_EOF && !endOfTreeList) {
                                // expect either a tree/utree id or the end tag
                                if (st.ttype == StreamTokenizer.TT_WORD) {
                                    if (st.sval.equalsIgnoreCase(endTag))
                                        endOfTreeList = true;
                                    else if (st.sval.equalsIgnoreCase(treeID) ||
                                             st.sval.equalsIgnoreCase(utreeID)) {
                                        // found the start of a tree
                                        st.nextToken();
                                        if (st.ttype == StreamTokenizer.TT_WORD) {
                                            returnList.add(st.sval); // found a tree name
                                        }
                                        while (st.nextToken() != StreamTokenizer.TT_EOF &&
                                               st.ttype != ';'); // find the end of the tree
                                    }
                                }
                                else st.nextToken(); // eat a non-word while looking for first tree word
                                    
                                // System.out.println("Not a word while looking for a tree start tag: " + st.ttype);
                            }
                        }
                        // not a tree section, find the end tag or the next start tag
                        else while (st.nextToken() != StreamTokenizer.TT_EOF &&
                                    st.ttype != StreamTokenizer.TT_WORD ||
                                    (!st.sval.equalsIgnoreCase(beginTag) && 
                                     !st.sval.equalsIgnoreCase(endTag)));
                    }
                    else
                        st.nextToken();
                }
                else
                    st.nextToken();
            }
            r.close();
        }
        catch (FileNotFoundException e) {
            System.err.println("Could not find file to identify: " + fileName);
        }
        catch (IOException e) {
            System.err.println("Couldn't identify file: " + fileName);
        }
        return returnList;
    }
    
    /**
     * Debug printout function.  Avoid using the system calls and use this, and set flag
     * {@link #debugOutput} depending on debugging or not.
     * @param s Display the string, for debugging.
     */
    public void debugOutput(String s) {
        if (debugOutput) {
            System.err.println(s);
        }
    }

    /**
     * Adds node at the top of the stack to the tree.  TreeNode is already created based
     * on Newick properties.
     * @param name Name of the node.
     * @param nodeStack Stack of nodes that haven't been added to the tree yet.  Nodes are popped when
     * they have names and all children are processed.
     * @return Newly added treeNode linked into the tree. 
     */
    private TreeNode popAndName(String name, Stack nodeStack) {
        TreeNode topNode = (TreeNode) nodeStack.pop();
        if (name == null) {
            topNode.label = "";
            topNode.setName("");
        } else {
            topNode.label = name;
            topNode.setName(name);
        }
        try {
            TreeNode parent = (TreeNode) nodeStack.peek();
            parent.addChild(topNode);
        } catch (EmptyStackException e) {
            if (topNode != rootNode) {
                System.err.println("Non-fatal EmptyStackException on topNode " + topNode);
            }
        }
        topNode.setExtremeLeaves(); // sets leftmost and rightmost leaf, non-recursive
        topNode.setNumberLeaves();  // sets number of leaves, non-recursive
        topNode.linkNodesInPreorder();
        topNode.linkNodesInPostorder();
        return topNode;
    }
    
    /**
     * Newick tokenizer: converts a string (tree as a string) into a tree object.
     * The stream tokenizer should be initialized before calling this function.
     * @param fileLength Length of the file, for progress bar movements.
     * For nexus files, this would be the relative position of the next semicolon = the size of the tree in bytes.
     * @param streamName Name of the tree or file that is being loaded.  Nexus files have names ("tree <name> = ((...));", newick trees are named by file name.
     * @param progressBar Reference to a progress bar widgit, embedded perhaps in place of the new canvas for this tree.  If this is null, create a new progress bar here.
     * @return Tree parsed from the stream.
     */
    public Tree tokenize(long fileLength, String streamName) {
        final char openBracket = '(',
            closeBracket = ')',
            childSeparator = ',',
            treeTerminator = lineTerminator,
            quote = '\'',
            doubleQuote = '"',
            infoSeparator = ':';
        int progress = 0;
        rootNode = new TreeNode();
        Tree t = new Tree();
        t.setRootNode(rootNode);
        t.setFileName(streamName);
        Stack nodeStack = new Stack();
        nodeStack.push(rootNode);
        int thisToken;
        TreeNode lastNamed = null;
        boolean EOT = false;
        boolean nameNext = true;
        int percentage = 0;
	try {
            while (EOT == false &&
                   (thisToken = tokenizer.nextToken()) != StreamTokenizer.TT_EOF) {
                switch (thisToken) {
                    // case quote:
                case doubleQuote:
                case StreamTokenizer.TT_WORD:
                    if (!nameNext) {
                        System.err.println("Error: didn't expect this name here: " + tokenizer.sval);
                    }
                    lastNamed = popAndName(tokenizer.sval, nodeStack);
                    progress += tokenizer.sval.length();
                    nameNext = false;
                    break;
                case StreamTokenizer.TT_NUMBER:
                    if (nameNext) {
                        lastNamed = popAndName(tokenizer.sval, nodeStack);
                    } else {
                        if (lastNamed != null) {
                            lastNamed.setWeight(tokenizer.nval);
                        } else {
                            System.err.println("Error: can't set value " + tokenizer.nval + " to a null node");
                            lastNamed = null;
                        }
                        progress += (new Double(tokenizer.nval).toString()).length();
                        nameNext = false;
                    }
                    break;
                case infoSeparator:
                    if (nameNext) {
                        lastNamed = popAndName(null, nodeStack);
                    }
                    progress += 1;
                    nameNext = false;
                    break;
                case treeTerminator:
                case StreamTokenizer.TT_EOF:
                    if (nameNext) {
                        lastNamed = popAndName(null, nodeStack);
                    }
                    EOT = true;
                    progress += 1;
                    nameNext = false;
                    break;
                case openBracket:
                    nodeStack.push(new TreeNode());
                    progress += 1;
                    nameNext = true;
                    break;
                case closeBracket:
                    if (nameNext) {
                        lastNamed = popAndName(null, nodeStack);
                    }
                    progress += 1;
                    nameNext = true;
                    break;
                case childSeparator:
                    if (nameNext) {
                        lastNamed = popAndName(null, nodeStack);
                    }
                    nodeStack.push(new TreeNode());
                    progress += 1;
                    nameNext = true;
                    break;
                default:
                    debugOutput("default " + (char)thisToken);
                    break;
                }
            }
        }
        catch (IOException e) {
        }
        if (!nodeStack.isEmpty()) {
            System.err.println("Node stack still has " + nodeStack.size() + " things");
        }
        t.postProcess();
        return t;
    }
    /**
     * Nexus taxa tokenizer, does nothing for now, but can be used later.
     *
     */
    private void nexusTaxaTokenize() {
        // taxa section stuff, we might be able to just throw this away, these are replicated everywhere else
        final String dimensionID = "dimensions", taxLabelID = "taxlabels";
    }
    
    /**
     * Tokenize the tree section of a nexus file only, uses newick tokenizer.
     * @param treeNumbers Vector of Integers for commandline-based input of nexus trees; assume this vector is in ascending order
     * @return arraylist of trees parsed from the tree file.
     */
    private LinkedList nexusTreeTokenize(Vector treeNumbers) {
        LinkedList treeArray = new LinkedList();
        final String
            titleTag = "title", linkTag = "link", translateTag = "translate";
        // newick tree subsection stuff (newick encoding)
        
        debugOutput("tokenizing tree section");
        boolean readAllTrees = true;
        boolean treeSectionEnd = false;
        boolean nextTreeID = false;
        int nextNumber = -1;
        int thisToken;
        int currTree = 0;
        String currTreeName = null;
        if (treeNumbers != null && treeNumbers.size() > 0) {
            readAllTrees = false;
            nextNumber = ((Integer)treeNumbers.get(0)).intValue();
            treeNumbers.remove(0);
        }
        while ((readAllTrees || nextNumber != -1) &&
               !treeSectionEnd)
            try {
                while (!treeSectionEnd &&
                       (thisToken = tokenizer.nextToken()) != StreamTokenizer.TT_EOF) {
                    switch (thisToken) {
                    case StreamTokenizer.TT_WORD:
                        if (nextTreeID) {
                            currTreeName = tokenizer.sval;
                            debugOutput("found tree ID: " + currTreeName);
                            nextTreeID = false;
                        } else if (tokenizer.sval.equalsIgnoreCase(treeID) ||
                                   tokenizer.sval.equalsIgnoreCase(utreeID))
                            {
                                debugOutput("new tree"); 
                                nextTreeID = true; // tree tag found, next word is a tree name
                            }
                        else if (tokenizer.sval.equalsIgnoreCase(endTag))
                            treeSectionEnd = true;
                        //                    	    debugOutput("TWord: " + tokenizer.sval);
                        break;
                    case equals:
                        {
                            if (treeNumbers == null || currTree == nextNumber)
                                {
                                    Tree t = tokenize(0, currTreeName);
                                    treeArray.add(t);
                                    if (treeNumbers != null && !treeNumbers.isEmpty())
                                        {
                                            nextNumber = ((Integer)treeNumbers.get(0)).intValue();
                                            treeNumbers.remove(0);
                                        }
                                    else
                                        nextNumber = -1;
                                }
                            currTree++;
                        }
                        break; // eat the equals
                    case commentOpen:
                        debugOutput("TEating comment");
                        while (thisToken != StreamTokenizer.TT_EOF && thisToken != commentClose)
                            {
                                thisToken = tokenizer.nextToken(); // eat the comments
                            }
                        break;
                    default:
                        debugOutput("Tdefault " + (char)thisToken);
                        break;
                    }
                }
            }
            catch (IOException e)
                {
                    System.err.println("Nexus tokenizer error: " + e);
                }
        return treeArray;
    }
    
    /**
     * Tokenize the character section of a nexus file only.  Does nothing for now, but
     * can be extended to handle sequences, for example.
     */
    private void nexusCharacterTokenize()
    {
        //      character section stuff, for sequence encodings and such
        //  (this parser may later extend to cover sequences)
        final String
            formatID = "format", numTaxaID = "ntax", numCharID = "nchar",
            dataTypeID = "datatype", gapID = "gap", missingID = "missing", matrixID = "matrix";
    }
    
    /**
     * Tokenize a nexus file, uses newick tokenizer after identifying the region with the tree information.
     * @param treeNumbers Vector of Integers for commandline-based input of nexus trees; assume this vector is in ascending order.
     * @return arraylist of trees parsed from the nexus file.
     */
    public LinkedList nexusTokenize(Vector treeNumbers)
    {
        System.out.println("Nexus tokenize: " + treeNumbers.toString());
        LinkedList treeArray = null;
        // Nexus string externalization: all strings are case insensitive
        final String 
    
            // the sections:
            //  characters - sequences
            characterTag = "character",
            //  taxa - a list of all taxa in this file?
            taxaTag = "taxa";
                
        boolean EOF = false;
        int thisToken;
        try
            {
                while (EOF == false &&
                       (thisToken = tokenizer.nextToken()) != StreamTokenizer.TT_EOF)
                    {
                        switch (thisToken)
                            {
                            case StreamTokenizer.TT_WORD:
                                if (tokenizer.sval.equalsIgnoreCase(nexusFileID)); // ignore
                                else if (tokenizer.sval.equalsIgnoreCase(beginTag))
                                    {
                                        debugOutput("beginning new section: " + tokenizer.sval);
                                        thisToken = tokenizer.nextToken();
                                        if (tokenizer.sval.equalsIgnoreCase(treeSectionTag))
                                            treeArray = nexusTreeTokenize(treeNumbers);
                                        else if (tokenizer.sval.equalsIgnoreCase(characterTag))
                                            nexusCharacterTokenize();
                                        else if (tokenizer.sval.equalsIgnoreCase(taxaTag))
                                            nexusTaxaTokenize();
                                    }
                                else debugOutput("Word: " + tokenizer.sval);
                                break;
                            case commentOpen:
                                debugOutput("Eating comment");
                                while (thisToken != StreamTokenizer.TT_EOF && thisToken != commentClose)
                                    {
                                        thisToken = tokenizer.nextToken(); // eat the comments
                                    }
                                break;
                            default:
                                debugOutput("default " + (char)thisToken);
                                break;
                            }
                    }
            }
        catch (IOException e)
            {
                System.err.println("Nexus tokenizer error: " + e);
            }
        return treeArray;
    }
    
    /**
     * Read a Tree from a file.
     */
    public static Tree readTree(File file) throws FileNotFoundException {
        return readTree(new BufferedReader(new FileReader(file)), file.length(), file.getName());
    }

    /**
     * Read a Tree from a BufferedReader, supplying the length and name.
     */
    public static Tree readTree(BufferedReader reader, long length, String name) {
        TreeParser tp = new TreeParser(reader);
        return tp.tokenize(length, name);
    }        

    /**
     * Test application function.
     * @param args list of filenames to parse
     */
    public static void main(String[] args) {
        String[] fileNames = args;
        long start = System.currentTimeMillis();
        for (String fileName : fileNames) {
            File file = new File(fileName);
            try {
                Tree t = readTree(file);
                // output
                System.out.println(t.getName()+" ("+t.getKey()+") has "+t.nodes.size()+" nodes and "+t.getLeafCount()+" leaves with height="+t.getHeight());
                for (TreeNode n : t.nodes) {
                    System.out.println(n);
                }
                recursivePrint(t, 0, 0);
                System.out.println("Parsed in " + ((System.currentTimeMillis() - start)/1000.0) + " s");
                System.out.println("------------------------------------------------------------------");
            } catch (FileNotFoundException e) {
                System.err.println(e);
            }
        }
    }
    
    static void recursivePrint (Tree tree, int currkey, int currdepth) {
        TreeNode currNode = tree.getNodeByKey(currkey);
        int numChildren = currNode.numberChildren();
        for (int i = 0; i < numChildren; i++) {
            int childkey = currNode.getChild(i).getKey();
            TreeNode childnode = tree.getNodeByKey(childkey);
            String childName = childnode.getName();
            if (childName.length()==0) childName = ".";
            System.out.println("child name:"+childName+" depth="+currdepth+" weight="+childnode.weight+" isRoot="+childnode.isRoot()+" isLeaf="+childnode.isLeaf());
            recursivePrint(tree, childkey, currdepth+1);
        }
    }
}
