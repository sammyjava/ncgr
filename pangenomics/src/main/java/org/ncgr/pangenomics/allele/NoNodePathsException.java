package org.ncgr.pangenomics.allele;

/**
 * An exception thrown when a Graph has no NodePaths populated (and needs to).
 */
public class NoNodePathsException extends Exception {
    public NoNodePathsException(String e) {
        super(e);
    }
}