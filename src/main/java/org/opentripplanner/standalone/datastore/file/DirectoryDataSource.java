package org.opentripplanner.standalone.datastore.file;

import org.apache.commons.io.FileUtils;
import org.opentripplanner.standalone.datastore.CompositeDataSource;
import org.opentripplanner.standalone.datastore.DataSource;
import org.opentripplanner.standalone.datastore.FileType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;


/**
 * This is a adapter to wrap a file directory and all files in it as a
 * composite data source. Sub-directories are ignored.
 */
public class DirectoryDataSource extends AbstractFileDataSource implements CompositeDataSource {

    public DirectoryDataSource(File path, FileType type) {
        super(path, type);
    }

    @Override
    @SuppressWarnings("ConstantConditions")
    public Collection<DataSource> content() {
        Collection<DataSource> content = new ArrayList<>();
        if(file.exists()) {
            for (File file : file.listFiles()) {
                // Skip any nested directories
                if (file.isDirectory()) { continue; }
                // In general the file type at a child level is not used, but we tag
                // each file with the same type as the parent directory.
                // Examples: GTFS or NETEX.
                content.add(new FileDataSource(file, type));
            }
        }
        return content;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public DataSource entry(String filename) {
        if(!file.exists()) {
            file.mkdirs();
        }
        return new FileDataSource(new File(file, filename), type);
    }

    @Override
    public void delete() {
        try {
            if(file.exists()) {
                FileUtils.deleteDirectory(file);
            }
        }
        catch (IOException e) {
            throw new IllegalStateException(e.getLocalizedMessage(), e);
        }
    }

    @Override
    public void delete(String entry) {
        File file = new File(this.file, entry);
        if(file.exists()) {
            if(!file.delete()) {
                throw new IllegalStateException("Unable to delete file: " + file.getPath());
            }
        }
    }

    @Override
    public void rename(String currentEntryName, String newEntryName) {
        File currentFile = new File(file, currentEntryName);
        File newFileFile = new File(file, newEntryName);

        if(!currentFile.exists()) {
            throw new IllegalArgumentException(
                    "Unable to move none existing file " + currentEntryName + " in " + path()
            );
        }
        try {
            FileUtils.moveFile(currentFile, newFileFile);
        }
        catch (IOException e) {
            throw new RuntimeException(
                    "Failed to rename " + path() + ": " + e.getLocalizedMessage(),
                    e
            );
        }
    }

    @Override
    public void close() { /* Nothing to close */ }
}
