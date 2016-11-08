package mobi.maptrek.plugin.cloudsync;

import java.util.Date;

class FileInfo {
    final long size;
    final Date lastModified;
    final String revision;

    FileInfo(long size, Date lastModified) {
        this.size = size;
        this.lastModified = lastModified;
        this.revision = null;
    }

    FileInfo(long size, Date lastModified, String revision) {
        this.size = size;
        this.lastModified = lastModified;
        this.revision = revision;
    }
}
