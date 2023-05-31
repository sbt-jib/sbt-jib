package de.gccc.jib;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipExtraField;
import org.apache.commons.compress.archivers.zip.ZipFile;

/**
 * Strips non-reproducible data from a ZIP file.
 * It rebuilds the ZIP file with a predictable order for the zip entries and sets zip entry dates to a fixed value.
 */
public final class ZipStripper implements Stripper
{
    // The ZipArchiveEntry.setXxxTime() methods write the time taking into account the local time zone,
    // so we must first convert the desired timestamp value in the local time zone to have the
    // same timestamps in the ZIP file when the project is built on another computer in a
    // different time zone.
    private static final long DEFAULT_ZIP_TIMESTAMP
            = LocalDateTime.of(2000, 1, 1, 0, 0, 0, 0).atZone(ZoneOffset.systemDefault())
            .toInstant().toEpochMilli();

    // CHECKSTYLE IGNORE LINE: ReturnCount
    /**
     * Comparator used to sort the files in the ZIP file.
     * This is mostly an alphabetical order comparator, with the exception that
     * META-INF/MANIFEST.MF and META-INF/ must be the 2 first entries (if they exist)
     * because this is required by some tools
     * (cf. https://github.com/Zlika/reproducible-build-maven-plugin/issues/16).
     */
    private static final Comparator<String> MANIFEST_FILE_SORT_COMPARATOR = (o1, o2) -> {
        if ("META-INF/MANIFEST.MF".equals(o1)) {
            return -1;
        }
        if ("META-INF/MANIFEST.MF".equals(o2)) {
            return 1;
        }
        if ("META-INF/".equals(o1)) {
            return -1;
        }
        if ("META-INF/".equals(o2)) {
            return 1;
        }
        return o1.compareTo(o2);
    };

    private final Map<String, Stripper> subFilters = new HashMap<>();

    private final long zipTimestamp;

    /**
     * Creates ZipStripper with default timestamp ({@link #DEFAULT_ZIP_TIMESTAMP}) for zip archive entries.
     */
    public ZipStripper()
    {
        zipTimestamp = DEFAULT_ZIP_TIMESTAMP;
    }

    /**
     * Creates ZipStripper with specified date and time for zip archive entries.
     *.
     * @param zipDateTime date and time for zip archive entries.
     */
    public ZipStripper(LocalDateTime zipDateTime)
    {
        zipTimestamp = zipDateTime.atZone(ZoneOffset.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * Adds a stripper for a given file in the Zip.
     * @param filename the name of the file in the Zip (regular expression).
     * @param stripper the stripper to apply on the file.
     * @return this object (for method chaining).
     */
    public ZipStripper addFileStripper(String filename, Stripper stripper)
    {
        subFilters.put(filename, stripper);
        return this;
    }

    @Override
    public void strip(File in, File out) throws IOException
    {
        try (final ZipFile zip = new ZipFile(in);
             final ZipArchiveOutputStream zout = new ZipArchiveOutputStream(out))
        {
            final List<String> sortedNames = sortEntriesByName(zip.getEntries());
            for (String name : sortedNames)
            {
                final ZipArchiveEntry entry = zip.getEntry(name);
                // Strip Zip entry
                final ZipArchiveEntry strippedEntry = filterZipEntry(entry);
                // Strip file if required
                final Stripper stripper = getSubFilter(name);
                if (stripper != null)
                {
                    // Unzip entry to temp file
                    final File tmp = File.createTempFile("tmp", null);
                    tmp.deleteOnExit();
                    Files.copy(zip.getInputStream(entry), tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    final File tmp2 = File.createTempFile("tmp", null);
                    tmp2.deleteOnExit();
                    stripper.strip(tmp, tmp2);
                    final byte[] fileContent = Files.readAllBytes(tmp2.toPath());
                    strippedEntry.setSize(fileContent.length);
                    zout.putArchiveEntry(strippedEntry);
                    zout.write(fileContent);
                    zout.closeArchiveEntry();
                }
                else
                {
                    // Copy the Zip entry as-is
                    zout.addRawArchiveEntry(strippedEntry, zip.getRawInputStream(entry));
                }
            }
        }
    }

    private Stripper getSubFilter(String name)
    {
        for (Entry<String, Stripper> filter : subFilters.entrySet())
        {
            if (name.matches(filter.getKey()))
            {
                return filter.getValue();
            }
        }
        return null;
    }

    private List<String> sortEntriesByName(Enumeration<ZipArchiveEntry> entries)
    {
        return Collections.list(entries).stream()
                .map(ZipArchiveEntry::getName)
                .sorted(MANIFEST_FILE_SORT_COMPARATOR)
                .collect(Collectors.toList());
    }

    private ZipArchiveEntry filterZipEntry(ZipArchiveEntry entry)
    {
        // Set times
        entry.setCreationTime(FileTime.fromMillis(zipTimestamp));
        entry.setLastAccessTime(FileTime.fromMillis(zipTimestamp));
        entry.setLastModifiedTime(FileTime.fromMillis(zipTimestamp));
        entry.setTime(zipTimestamp);
        // Remove extended timestamps
        for (ZipExtraField field : entry.getExtraFields())
        {
            if (field instanceof X5455_ExtendedTimestamp)
            {
                entry.removeExtraField(field.getHeaderId());
            }
        }
        return entry;
    }
}
