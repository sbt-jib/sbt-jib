package de.gccc.jib;

import java.io.File;
import java.io.IOException;

/**
 * Generic interface for stripping non-reproducible data.
 */
interface Stripper
{
    /**
     * Strips non-reproducible data.
     * @param in the input file.
     * @param out the stripped output file.
     * @throws IOException if an I/O error occurs.
     */
    void strip(File in, File out) throws IOException;
}
