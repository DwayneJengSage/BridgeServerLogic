package org.sagebionetworks.bridge.upload;

/**
 * This class represents the size of an UploadFieldDefinition (or a list of UploadFieldDefinitions), both in column
 * count and in bytes (as measured in the Synapse table).
 */
public class UploadFieldSize {
    private final int numBytes;
    private final int numColumns;

    /** Constructs an UploadFieldSize. */
    public UploadFieldSize(int numBytes, int numColumns) {
        this.numBytes = numBytes;
        this.numColumns = numColumns;
    }

    /** Number of bytes the field definition or definitions take up in a Synapse table. */
    public int getNumBytes() {
        return numBytes;
    }

    /** Number of Synapse table columns generated by the field definition or definitions. */
    public int getNumColumns() {
        return numColumns;
    }
}