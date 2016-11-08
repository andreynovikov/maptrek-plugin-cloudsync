package mobi.maptrek.plugin.cloudsync;

import java.util.Date;

class FileInfo {
    final long size;
    final Date lastModified;

    FileInfo(long size, Date lastModified) {
        this.size = size;
        this.lastModified = lastModified;
    }
}
