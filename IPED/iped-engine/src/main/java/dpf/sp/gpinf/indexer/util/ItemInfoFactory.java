package dpf.sp.gpinf.indexer.util;

import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;
import iped3.IItem;

public class ItemInfoFactory {

    public static ItemInfo getItemInfo(IItem evidence) {
        ItemInfo info = new ItemInfo(evidence.getId(), evidence.getHash(), evidence.getLabels(),
                evidence.getCategorySet(), evidence.getPath(), evidence.isCarved());
        // info.setEvidence(evidence);
        return info;
    }

}