package org.ncgr.jgraph;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.StringJoiner;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.Map;
import java.util.HashMap;

/**
 * Encapsulates a set of nodes in a Graph. NodeSets are comparable based on their content.
 *
 * @author Sam Hokin
 */
public class NodeSet extends TreeSet<Node> implements Comparable<NodeSet> {

    public int totalBases;

    /**
     * Empty constructor.
     */
    public NodeSet() {
        super();
    }
    
    /**
     * Construct given a TreeSet of Nodes.
     */
    public NodeSet(Collection<Node> nodes) {
        this.addAll(nodes);
        update();
    }

    /**
     * Construct from a string representation, e.g. "[5,7,15,33]" and the underlying graph.
     */
    public NodeSet(PangenomicGraph graph, String str) {
        Set<Node> graphNodes = graph.vertexSet();
        Map<Long,Node> graphNodeMap = new HashMap<>();
        for (Node n : graphNodes) graphNodeMap.put(n.getId(), n);
        Set<String> nodeStrings = new HashSet<>(Arrays.asList(str.replace("[","").replace("]","").split(",")));
        for (String s : nodeStrings) {
            long id = Long.parseLong(s);
            if (graphNodeMap.containsKey(id)) this.add(graphNodeMap.get(id));
        }
    }

    /**
     * Update derived quantities like totalBases.
     */
    public void update() {
        String bases = "";
        for (Node n : this) {
            bases += n.sequence;
        }
        this.totalBases = bases.length();
    }

    /**
     * Equality if exactly the same nodes.
     */
    public boolean equals(NodeSet that) {
        return this.containsAll(that) && that.containsAll(this);
    }

    /**
     * Compare based on size, then node IDs, one by one.
     */
    public int compareTo(NodeSet that) {
        if (this.equals(that)) {
            return 0;
        } else if (this.size()!=that.size()) {
            return this.size() - that.size();
        } else {
            Node thisNode = this.first();
            Node thatNode = that.first();
            while (thisNode.equals(thatNode)) {
                thisNode = this.higher(thisNode);
                thatNode = that.higher(thatNode);
            }
            return thisNode.compareTo(thatNode);
        }
    }

    /**
     * Return a readable summary string.
     */
    public String toString() {
        String s = "[";
        StringJoiner joiner = new StringJoiner(",");
        for (Node node : this) {
            joiner.add(String.valueOf(node.id));
        }
        s += joiner.toString();
        s += "]";
        return s;
    }

    /**
     * Return true if this NodeSet is a parent of the given NodeSet, meaning its nodes are a subset of the latter.
     */
    public boolean parentOf(NodeSet that) {
        return that.size()>this.size() && that.containsAll(this);
    }

    /**
     * Return true if this NodeSet is a child of the given NodeSet, meaning its nodes are a superset of the latter.
     */
    public boolean childOf(NodeSet that) {
        return that.size()<this.size() && this.containsAll(that);
    }

    /**
     * Return the result of merging two NodeSets.
     * NOTE: does NOT run update() on the result!
     */
    public static NodeSet merge(NodeSet ns1, NodeSet ns2) {
        NodeSet merged = new NodeSet();
        merged.addAll(ns1);
        merged.addAll(ns2);
        return merged;
    }
}
    