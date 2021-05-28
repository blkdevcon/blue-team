package dpf.sp.gpinf.indexer.process.task;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.dpf.sepinf.photodna.api.PhotoDNA;
import dpf.sp.gpinf.indexer.util.UTF8Properties;
import iped3.IItem;

public class PhotoDNATask extends AbstractTask {

    private Logger LOGGER = LoggerFactory.getLogger(PhotoDNATask.class);

    public static final String PDNA_NOT_FOUND_MSG = "Optional photoDNA lib not found in plugin/optional_jars folder. If you are law enforcement, ask sepinf.inc@pf.gov.br";

    public static final int HASH_SIZE = 144;

    public static final String ENABLE_PHOTO_DNA = "enablePhotoDNA";

    public static final String PHOTO_DNA = "photoDNA";

    public static final String CONFIG_FILE = "PhotoDNAConfig.txt";

    public static final String USE_THUMBNAIL = "computeFromThumbnail";

    public static final String MIN_FILE_SIZE = "minFileSize";

    public static final String SKIP_HASH_DB_FILES = "skipHashDBFiles";

    public static final String STATUS_HASH_DB_FILTER = "statusHashDBFilter";

    public static final String MAX_SIMILARITY_DISTANCE = "maxSimilarityDistance";

    public static final String TEST_ROTATED_FLIPPED = "searchRotatedAndFlipped";

    private static AtomicBoolean warned = new AtomicBoolean();

    private PhotoDNA photodna;

    private boolean enabled = false;

    private boolean useThumbnail = true;

    private int minFileSize = 10000;

    private boolean skipHashDBFiles = true;

    @Override
    public void init(Properties confParams, File confDir) throws Exception {

        String value = confParams.getProperty(ENABLE_PHOTO_DNA);
        if (value != null && !value.trim().isEmpty())
            enabled = Boolean.valueOf(value.trim());

        if (!enabled)
            return;

        UTF8Properties config = new UTF8Properties();
        config.load(new File(confDir, CONFIG_FILE));
        value = config.getProperty(USE_THUMBNAIL);
        if (value != null && !value.trim().isEmpty())
            useThumbnail = Boolean.valueOf(value.trim());

        value = config.getProperty(MIN_FILE_SIZE);
        if (value != null && !value.trim().isEmpty())
            minFileSize = Integer.valueOf(value.trim());

        value = config.getProperty(SKIP_HASH_DB_FILES);
        if (value != null && !value.trim().isEmpty())
            skipHashDBFiles = Boolean.valueOf(value.trim());

        value = config.getProperty(STATUS_HASH_DB_FILTER);
        if (value != null && !value.trim().isEmpty())
            PhotoDNALookup.statusHashDBFilter = value.trim();

        value = config.getProperty(MAX_SIMILARITY_DISTANCE);
        if (value != null && !value.trim().isEmpty())
            PhotoDNALookup.MAX_DISTANCE = Integer.valueOf(value.trim());

        value = config.getProperty(TEST_ROTATED_FLIPPED);
        if (value != null && !value.trim().isEmpty())
            PhotoDNALookup.rotateAndFlip = Boolean.valueOf(value.trim());

        try {
            Class<?> c = Class.forName("br.dpf.sepinf.photodna.PhotoDNA");
            photodna = (PhotoDNA) c.newInstance();

        } catch (ClassNotFoundException e) {
            enabled = false;
            if (!warned.getAndSet(true))
                LOGGER.error(PDNA_NOT_FOUND_MSG);
        }

    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void finish() throws Exception {

    }

    @Override
    protected void process(IItem evidence) throws Exception {

        if (!evidence.isToAddToCase())
            return;

        byte[] thumb = evidence.getThumb(); 
        if (thumb == null || !evidence.getMediaType().getType().equals("image"))
            return;

        if (evidence.getLength() != null && evidence.getLength() < minFileSize)
            return;

        if (useThumbnail && thumb.length == 0)
            return;

        if (skipHashDBFiles && evidence.getExtraAttribute(HashDBLookupTask.STATUS_ATTRIBUTE) != null)
            return;

        byte[] hash;
        try (InputStream is = useThumbnail ? new ByteArrayInputStream(thumb)
                : evidence.getBufferedStream()) {

            photodna.reset();
            hash = photodna.computePhotoDNA(is);
            String hashStr = new String(Hex.encodeHex(hash, false));
            evidence.setExtraAttribute(PHOTO_DNA, hashStr);

        } catch (Throwable e) {
            LOGGER.info("Error computing photoDNA for " + evidence.getPath(), e);
            evidence.setExtraAttribute("photodna_exception", e.toString());
            return;
        }

    }

}
