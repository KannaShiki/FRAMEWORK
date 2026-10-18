package mg.itu.etu004361;

public class MapFile {
    private String filename;
    private String extension;
    private byte[] bytes;

    public MapFile() {
    }

    public MapFile(String filename, String extension, byte[] bytes) {
        this.filename = filename;
        this.extension = extension;
        this.bytes = bytes;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getFileName() {
        return filename;
    }

    public void setFileName(String filename) {
        this.filename = filename;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }

    public boolean hasContent() {
        return bytes != null && bytes.length > 0;
    }

    public boolean isEmpty() {
        return !hasContent() && (filename == null || filename.isEmpty());
    }
}
