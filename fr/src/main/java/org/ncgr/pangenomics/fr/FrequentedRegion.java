package org.ncgr.pangenomics.fr;

import java.util.Iterator;
import java.util.List;
import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a cluster of nodes along with the supporting subpaths of the full set of strain/subject/subspecies paths.
 *
 * @author Sam Hokin
 */
public class FrequentedRegion implements Comparable<FrequentedRegion> {

    // this cluster's nodes
    TreeSet<Long> nodes;

    // the sequences for ALL nodes, keyed by nodeId
    Map<Long,String> nodeSequences;

    // the full strain/subject/subspecies paths, keyed and sorted by path name and nodes
    // NOTE: this could alternatively be the originating Graph instance, since it has the paths as well.
    TreeSet<Path> paths;

    // the "maximal" subpaths, identified by their originating path name, that start and end on nodes in this cluster
    TreeSet<Path> subpaths;
    
    // the forward (or total, if rc not enabled) support of this cluster
    int fwdSupport;
    
    // the reverse-complement support of this cluster (if enabled)
    // NOT YET IMPLEMENTED
    int rcSupport;

    // a subpath must satisfy the requirement that it traverses at least alpha*nodes.size()
    double alpha;

    // a subpath must satisfy the requirement that its contiguous nodes that do NOT belong in this.nodes have total sequence length no larger than kappa
    int kappa;

    // the (rounded) average length (in bases) of the subpath sequences
    int avgLength;

    /**
     * Construct given a set of nodes and full paths and sequences, along with alpha and kappa filter parameters.
     */
    FrequentedRegion(TreeSet<Long> nodes, TreeSet<Path> paths, Map<Long,String> nodeSequences, double alpha, int kappa) {
        this.nodes = nodes;
        this.paths = paths;
        this.nodeSequences = nodeSequences;
        this.alpha = alpha;
        this.kappa = kappa;
        // compute the subpaths, average length, support.
        update();
    }

    /**
     * Update this frequented region with its existing contents and the current alpha,kappa values.
     * This should be run any time a new FrequentedRegion is made.
     */
    void update() {
        updateSubpaths();
        updateSupport();  // must follow updatesubPaths() since some paths may have been dropped
        updateAvgLength();
    }

    /**
     * Equality is simply based on the nodes in the frequented region.
     */
    public boolean equals(FrequentedRegion that) {
        return this.nodes.equals(that.nodes);
    }
    
    /**
     * Compare based on support and then number of nodes and finally the first node.
     */
    public int compareTo(FrequentedRegion that) {
        if (this.fwdSupport!=that.fwdSupport) {
            return Integer.compare(this.fwdSupport, that.fwdSupport);
        } else if (this.avgLength!=that.avgLength) {
            return Integer.compare(this.avgLength, that.avgLength);
        } else if (this.nodes.size()!=that.nodes.size()) {
            return Integer.compare(this.nodes.size(), that.nodes.size());
        } else {
            return Long.compare(this.nodes.first(), that.nodes.first());
        }
    }

    /**
     * Update the current average length this frequented region's subpath sequences.
     */
    void updateAvgLength() {
        double dAvg = 0.0;
        for (Path subpath : subpaths) {
            for (long nodeId : subpath.nodes) {
                String sequence = nodeSequences.get(nodeId);
                if (sequence==null) {
                    // bail, this is an error
                    System.err.println("ERROR: sequence is null for node "+nodeId);
                    System.exit(1);
                } else {
                    dAvg += (double) sequence.length();
                }
            }
        }
        avgLength = (int)(dAvg/subpaths.size());
    }

    /**
     * Update the subpaths from the full genome paths for the current alpha and kappa values.
     * NOTE: NOT FINISHED, NEED TO IMPLEMENT kappa FILTER.
     */
    void updateSubpaths() {
        // // DEBUG
        // FRFinder.printHeading("FrequentedRegion:"+nodes+" alpha="+alpha+" kappa="+kappa);
        TreeSet<Path> newSubpaths = new TreeSet<>();
        int count = 0;
        for (Path path : paths) {
            long left = 0;
            long right = 0;
            for (long nodeId : path.nodes) {
                if (nodes.contains(nodeId)) {
                    if (left==0) {
                        left = nodeId;
                    } else {
                        right = nodeId;
                    }
                }
            }
            LinkedList<Long> subpathNodes = new LinkedList<>();
            if (left!=0 && right==0) {
                // singleton, do nothing
                // subpathNodes.add(left);
            } else if (left!=0 && right!=0) {
                // load the insertions into a set for the kappa filter
                Set<List<Long>> insertions = new HashSet<>();
                // scan across the full path from left to right to create the subpath
                boolean started = false;
                boolean ended = false;
                List<Long> currentInsertion = new LinkedList<>();
                for (long nodeId : path.nodes) {
                    if (nodeId==left) {
                        // left always matches a cluster node
                        started = true;
                        subpathNodes.add(nodeId);
                    } else if (nodeId==right) {
                        // right always matches a cluster node
                        ended = true;
                        subpathNodes.add(nodeId);
                        break;
                    } else if (started && !ended) {
                        // all path nodes between left and right are in the subpath
                        subpathNodes.add(nodeId);
                        if (nodes.contains(nodeId)) {
                            if (currentInsertion.size()>0) {
                                // finish off the previous insertion and start another
                                insertions.add(currentInsertion);
                                currentInsertion = new LinkedList<>();
                            }
                        } else {
                            // add to the current insertion
                            currentInsertion.add(nodeId);
                        }
                    }
                }
                // hit the last insertion
                if (currentInsertion.size()>0) {
                    insertions.add(currentInsertion);
                }
                // kappa filter goes here
                boolean kappaOK = true;
                for (List<Long> insertion : insertions) {
                    String insertionSequence = "";
                    for (long nodeId : insertion) {
                        insertionSequence += nodeSequences.get(nodeId);
                    }
                    if (insertionSequence.length()>kappa) {
                        kappaOK = false;
                    }
                    // // DEBUG
                    // System.out.println("["+path.name+"]"+subpathNodes.toString().replace(" ","")+
                    //                    " left,right="+left+","+right+" insertion:"+insertion.toString().replace(" ","")+
                    //                    " length="+insertionSequence.length());
                }
                if (!kappaOK) {
                    // // DEBUG
                    // System.out.println("["+path.name+"]"+subpathNodes.toString().replace(" ","")+" REJECTED: one or more insertion sequences > than kappa="+kappa+".");
                    continue; // bail, kappa filter failed
                }
                // // DEBUG
                // if (insertions.size()>0) {
                //     System.out.println("["+path.name+"]"+subpathNodes.toString().replace(" ","")+" ACCEPTED: all insertion sequences <= kappa="+kappa+".");
                // } else {
                //     System.out.println("["+path.name+"]"+subpathNodes.toString().replace(" ","")+" NO INSERTIONS");
                // }
            }
            // alpha filter
            boolean alphaOK = true;
            int in = 0;
            for (long nodeId : nodes) {
                if (subpathNodes.contains(nodeId)) {
                    in++;
                }
            }
            double frac = (double)in/(double)nodes.size();
            alphaOK = frac>=alpha;
            if (!alphaOK) {
                // // DEBUG
                // if (frac>0.0) {
                //     System.out.println("["+path.name+"]"+subpathNodes.toString().replace(" ","")+" REJECTED: in="+in+", frac="+frac+" < alpha="+alpha);
                // }
                continue; // bail, alpha filter failed
            }
            // // DEBUG
            // System.out.println("["+path.name+"]"+subpathNodes.toString().replace(" ","")+" in="+in);
            // filters passed, add this subpath
            newSubpaths.add(new Path(path.name, path.category, subpathNodes));
        }
        // replace the instance subpaths with the new set
        subpaths = newSubpaths;
    }

    /**
     * Update the current support of this frequented region, which right now is just the size of the subpaths map.
     */
    void updateSupport() {
        fwdSupport = subpaths.size();
    }

    /**
     * Set a new alpha value.
     */
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    /**
     * Set a new kappa value.
     */
    public void setKappa(int kappa) {
        this.kappa = kappa;
    }

    /**
     * Return a string summary of this frequented region.
     */
    public String toString() {
        String s = "";
        for (long nodeId : nodes) {
            s += " "+nodeId;
        }
        Map<String,Integer> categoryCount = new TreeMap<>();
        for (Path subpath : subpaths) {
            if (categoryCount.containsKey(subpath.category)) {
                int count = categoryCount.get(subpath.category);
                categoryCount.put(subpath.category, count+1);
            } else {
                categoryCount.put(subpath.category, 1);
            }
            // s += "\n "+subpath.getLabel()+subpath.nodes.toString().replace(" ","").replace("[","").replace("]","");
        }
        int len = s.length();
        for (int i=8; i<=56; i+=8) if (len<i) s += "\t";
        s += avgLength+"\t"+fwdSupport;
        for (String category : categoryCount.keySet()) {
            s += " "+category+":"+categoryCount.get(category);
        }
        return s;
    }

    /**
     * Merge two FrequentedRegions and return the result.
     */
    static FrequentedRegion merge(FrequentedRegion nc1, FrequentedRegion nc2, double alpha, int kappa) {
        TreeSet<Long> nodes = new TreeSet<>();
        TreeSet<Path> paths = new TreeSet<>();
        Map<Long,String> nodeSequences = new TreeMap<>();
        nodes.addAll(nc1.nodes);
        nodes.addAll(nc2.nodes);
        nodeSequences.putAll(nc1.nodeSequences);
        nodeSequences.putAll(nc2.nodeSequences);
        paths.addAll(nc1.paths);
        paths.addAll(nc2.paths);
        // // DEBUG
        // System.out.println("merge: nodes="+nodes);
        // for (String pathName : paths.keySet()) {
        //     long left = 0;
        //     long right = 0;
        //     for (long nodeId : paths.get(pathName)) {
        //         if (nodes.contains(nodeId)) {
        //             left = nodeId;
        //             break;
        //         }
        //     }
        //     Iterator<Long> it = paths.get(pathName).descendingIterator();
        //     while (it.hasNext()) {
        //         long nodeId = it.next();
        //         if (nodes.contains(nodeId)) {
        //             right = nodeId;
        //             break;
        //         }
        //     }
        //     System.out.print("path="+pathName+" left="+left+" right="+right+":");
        //     boolean started = false;
        //     boolean ended = false;
        //     for (long nodeId : paths.get(pathName)) {
        //         if (nodeId==left) {
        //             started = true;
        //         }
        //         if (started && !ended) System.out.print(" "+nodeId);
        //         if (nodeId==right) {
        //             ended = true;
        //         }
        //     }
        //     System.out.println(" "+paths.get(pathName));
        // }
        return new FrequentedRegion(nodes, paths, nodeSequences, alpha, kappa);
    }

    /**
     * Return true if this FR contains a subpath which belongs to the given Path.
     */
    public boolean containsSubpathOf(Path path) {
        for (Path sp : subpaths) {
            if (sp.name.equals(path.name)) return true;
        }
        return false;
    }

    /**
     * Return a count of subpaths of FR that belong to the given Path.
     */
    public int countSubpathsOf(Path path) {
        int count = 0;
        for (Path sp : subpaths) {
            if (sp.name.equals(path.name)) count++;
        }
        return count;
    }

}