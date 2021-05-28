package dpf.inc.sepinf.UsnJrnl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class UsnJrnlEntry {
    private long offset;
    private int tam;
    private int majorVersion;
    private int minorVersion;
    private byte[] mftRef;
    private byte[] parentMftRef;
    private long USN;
    private Date fileTime;
    private long reasonFlag;
    private long sourceInformation;
    private long securityId;
    private long fileAttributes;
    private int sizeofFileName;
    private int offsetFilename;
    private String fileName;
    private String fullPath;

    private static HashMap<Integer, String> reasonFlags;
    private static HashMap<Integer, String> fileAttributesToString;

    static {
        reasonFlags = new HashMap<>();
        reasonFlags.put(0x01, "DATA_OVERWRITE");
        reasonFlags.put(0x02, "DATA_EXTEND");
        reasonFlags.put(0x04, "DATA_TRUNCATION");
        reasonFlags.put(0x10, "NAMED_DATA_OVERWRITE");
        reasonFlags.put(0x20, "NAMED_DATA_EXTEND");
        reasonFlags.put(0x40, "NAMED_DATA_TRUNCATION");
        reasonFlags.put(0x100, "FILE_CREATE");
        reasonFlags.put(0x200, "FILE_DELETE");
        reasonFlags.put(0x400, "EXTENDED_ATTRIBUTE_CHANGE");
        reasonFlags.put(0x800, "SECURITY_CHANGE");
        reasonFlags.put(0x1000, "RENAME_OLD_NAME");
        reasonFlags.put(0x2000, "RENAME_NEW_NAME");
        reasonFlags.put(0x4000, "INDEXABLE_CHANGE");
        reasonFlags.put(0x8000, "BASIC_INFO_CHANGE");
        reasonFlags.put(0x10000, "HARD_LINK_CHANGE");
        reasonFlags.put(0x20000, "COMPRESSION_CHANGE");
        reasonFlags.put(0x40000, "ENCRYPTION_CHANGE");
        reasonFlags.put(0x80000, "OBJECT_ID_CHANGE");
        reasonFlags.put(0x100000, "REPARSE_POINT_CHANGE");
        reasonFlags.put(0x200000, "STREAM_CHANGE");
        reasonFlags.put(0x80000000, "CLOSE");

        fileAttributesToString = new HashMap<>();
        fileAttributesToString.put(0x01, "READONLY");
        fileAttributesToString.put(0x02, "HIDDEN");
        fileAttributesToString.put(0x04, "SYSTEM");
        fileAttributesToString.put(0x10, "DIRECTORY");
        fileAttributesToString.put(0x20, "ARCHIVE");
        fileAttributesToString.put(0x40, "DEVICE");
        fileAttributesToString.put(0x80, "NORMAL");
        fileAttributesToString.put(0x100, "TEMPORARY");
        fileAttributesToString.put(0x200, "SPARSE_FILE");
        fileAttributesToString.put(0x400, "REPARSE_POINT");
        fileAttributesToString.put(0x800, "COMPRESSED");
        fileAttributesToString.put(0x1000, "OFFILINE_STORAGE");
        fileAttributesToString.put(0x2000, "NOT_INDEXED");
        fileAttributesToString.put(0x4000, "ENCRYPTED");
        fileAttributesToString.put(0x8000, "INTEGRITY_STREAM");
        fileAttributesToString.put(0x10000, "VIRTUAL");
        fileAttributesToString.put(0x20000, "NO_SCRUB_DATA");

    }

    public List<String> getReasons() {

        ArrayList<String> list = new ArrayList<>();

        for (int k : reasonFlags.keySet()) {
            if ((k & reasonFlag) != 0) {
                list.add(reasonFlags.get(k));
            }
        }

        return list;

    }

    public List<String> getHumanAttributes() {

        ArrayList<String> list = new ArrayList<>();

        for (int k : fileAttributesToString.keySet()) {
            if ((k & this.fileAttributes) != 0) {
                list.add(fileAttributesToString.get(k));
            }
        }

        return list;

    }

    public int getMajorVersion() {
        return majorVersion;
    }

    public void setMajorVersion(int majorVersion) {
        this.majorVersion = majorVersion;
    }

    public int getMinorVersion() {
        return minorVersion;
    }

    public void setMinorVersion(int minorVersion) {
        this.minorVersion = minorVersion;
    }

    public byte[] getMftRef() {
        return mftRef;
    }

    public void setMftRef(byte[] mftRef) {
        this.mftRef = mftRef;
    }

    public byte[] getParentMftRef() {
        return parentMftRef;
    }

    public long getMftParentRecord() {
        return ((long) parentMftRef[5] & 0xFF) << 40 | ((long) parentMftRef[4] & 0xFF) << 32
                | ((long) parentMftRef[3] & 0xFF) << 24 | ((long) parentMftRef[2] & 0xFF) << 16
                | ((long) parentMftRef[1] & 0xFF) << 8 | (long) parentMftRef[0] & 0xFF;
    }

    public int getMftParentSequence() {
        return ((int) parentMftRef[7] & 0xFF) << 8 | (int) parentMftRef[6] & 0xFF;
    }

    public long getParentMftRefAsLong() {
        return (long) getMftParentSequence() << 48 | getMftParentRecord();
    }

    public void setParentMftRef(byte[] parentMftRef) {
        this.parentMftRef = parentMftRef;
    }

    public long getUSN() {
        return USN;
    }

    public void setUSN(long uSN) {
        USN = uSN;
    }

    public Date getFileTime() {
        return fileTime;
    }

    public void setFileTime(Date fileTime) {
        this.fileTime = fileTime;
    }

    /** Difference between Filetime epoch and Unix epoch (in ms). */
    private static final long FILETIME_EPOCH_DIFF = 11644473600000L;

    /** One millisecond expressed in units of 100s of nanoseconds. */
    private static final long FILETIME_ONE_MILLISECOND = 10 * 1000;

    public static long filetimeToMillis(final long filetime) {
        return (filetime / FILETIME_ONE_MILLISECOND) - FILETIME_EPOCH_DIFF;
    }

    public void setFileTime(long fileTime) {
        this.fileTime = new Date(filetimeToMillis(fileTime));
    }

    public long getReasonFlag() {
        return reasonFlag;
    }

    public void setReasonFlag(long reasonFlag) {
        this.reasonFlag = reasonFlag;
    }

    public long getSourceInformation() {
        return sourceInformation;
    }

    public void setSourceInformation(long sourceInformation) {
        this.sourceInformation = sourceInformation;
    }

    public long getSecurityId() {
        return securityId;
    }

    public void setSecurityId(long securityId) {
        this.securityId = securityId;
    }

    public long getFileAttributes() {
        return fileAttributes;
    }

    public void setFileAttributes(long fileAttributes) {
        this.fileAttributes = fileAttributes;
    }

    public int getSizeofFileName() {
        return sizeofFileName;
    }

    public void setSizeofFileName(int sizeofFileName) {
        this.sizeofFileName = sizeofFileName;
    }

    public int getOffsetFilename() {
        return offsetFilename;
    }

    public void setOffsetFilename(int offsetFilename) {
        this.offsetFilename = offsetFilename;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getTam() {
        return tam;
    }

    public void setTam(int tam) {
        this.tam = tam;
    }

    public long getOffset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    public String getFullPath() {
        if (fullPath != null)
            return fullPath;
        else
            return "-";
    }

    public void setFullPath(String fullPath) {
        this.fullPath = fullPath;
    }

}
