package gpinf.dev.filetypes;

import java.io.File;
import java.util.List;

import dpf.sp.gpinf.indexer.Messages;
import iped3.IItem;

public class AttachmentFileType extends EvidenceFileType {

    /**
     * Implementação da classe base utilizada para anexos de email.
     *
     * @author Nassif (GPINF/SP)
     */
    private static final long serialVersionUID = 1839778399524523300L;

    @Override
    public String getLongDescr() {
        return Messages.getString("AttachmentFileType.EmailAttach"); //$NON-NLS-1$
    }

    @Override
    public void processFiles(File baseDir, List<IItem> items) {
        // TODO Auto-generated method stub

    }
    /*
     * @Override public Icon getIcon() { // TODO Auto-generated method stub return
     * null; }
     */

}
