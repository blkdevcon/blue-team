/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package dpf.sp.gpinf.indexer.search;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.Versao;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.Util;
import iped3.IIPEDSource;
import iped3.search.IMarcadores;
import iped3.search.LuceneSearchResult;

public class Marcadores implements Serializable, IMarcadores {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static Logger LOGGER = LoggerFactory.getLogger(Marcadores.class);

    public static String EXT = "." + Versao.APP_EXT.toLowerCase(); //$NON-NLS-1$
    public static String STATEFILENAME = "marcadores" + EXT; //$NON-NLS-1$

    static int labelBits = Byte.SIZE;

    private boolean[] selected;
    private ArrayList<byte[]> labels;
    private TreeMap<Integer, String> labelNames = new TreeMap<Integer, String>();
    private TreeMap<Integer, String> labelComments = new TreeMap<Integer, String>();
    private Set<Integer> reportLabels = new TreeSet<Integer>();

    private int selectedItens = 0, totalItems, lastId;

    private LinkedHashSet<String> typedWords = new LinkedHashSet<String>();
    private File indexDir;
    private File stateFile, cookie;

    private transient IIPEDSource ipedCase;

    public Marcadores(IIPEDSource ipedCase, File modulePath) {
        this(ipedCase.getTotalItens(), ipedCase.getLastId(), modulePath);
        this.ipedCase = ipedCase;
    }

    public Marcadores(int totalItens, int lastId, final File modulePath) {
        this.totalItems = totalItens;
        this.lastId = lastId;
        selected = new boolean[lastId + 1];
        labels = new ArrayList<byte[]>();
        indexDir = new File(modulePath, "index"); //$NON-NLS-1$
        stateFile = new File(modulePath, STATEFILENAME);
        updateCookie();
        try {
            stateFile = stateFile.getCanonicalFile();
        } catch (IOException e) {
        }
    }

    public void updateCookie() {
        long date = indexDir.lastModified();
        String tempdir = System.getProperty("java.io.basetmpdir"); //$NON-NLS-1$
        if (tempdir == null)
            tempdir = System.getProperty("java.io.tmpdir"); //$NON-NLS-1$
        cookie = new File(tempdir, "indexer" + date + EXT); //$NON-NLS-1$
    }

    public int getLastId() {
        return lastId;
    }

    public int getTotalItens() {
        return this.totalItems;
    }

    public File getIndexDir() {
        return indexDir;
    }

    public Map<Integer, String> getLabelMap() {
        return labelNames;
    }

    public LinkedHashSet<String> getTypedWords() {
        return typedWords;
    }

    public int getTotalSelected() {
        return selectedItens;
    }

    public boolean isSelected(int id) {
        return selected[id];
    }

    public void clearSelected() {
        selectedItens = 0;
        for (int i = 0; i < selected.length; i++) {
            selected[i] = false;
        }
    }

    public void selectAll() {
        selectedItens = totalItems;
        int maxLuceneId = ipedCase.getReader().maxDoc() - 1;
        for (int i = 0; i <= maxLuceneId; i++) {
            selected[ipedCase.getId(i)] = true;
        }
    }

    public List<String> getLabelList(int itemId) {
        ArrayList<Integer> labelIds = getLabelIds(itemId);
        TreeSet<String> result = new TreeSet<>();
        for (Integer labelId : labelIds) {
            result.add(labelNames.get(labelId));
        }
        ArrayList<String> list = new ArrayList<>(result.size());
        list.addAll(result);
        return list;
    }

    public ArrayList<Integer> getLabelIds(int id) {
        ArrayList<Integer> labelIds = new ArrayList<Integer>();
        if (labelNames.size() > 0)
            for (int i : labelNames.keySet()) {
                if (hasLabel(id, i))
                    labelIds.add(i);
            }

        return labelIds;
    }

    public void addLabel(List<Integer> ids, int label) {
        int labelOrder = label / labelBits;
        int labelMod = label % labelBits;
        int labelMask = 1 << labelMod;
        byte[] labelBytes = labels.get(labelOrder);
        for (int i = 0; i < ids.size(); i++) {
            int id = ids.get(i);
            labelBytes[id] |= labelMask;
        }

    }

    public int getLabelCount(int label) {
        if (labels.isEmpty()) {
            return 0;
        }
        int labelOrder = label / labelBits;
        int labelMask = 1 << (label % labelBits);
        byte[] labelBytes = labels.get(labelOrder); 
        int ret = 0;
        for (byte b : labelBytes) {
            if ((b & labelMask) != 0) {
                ret++;
            }
        }
        return ret;
    }
    
    public final boolean hasLabel(int id) {
        boolean hasLabel = false;
        for (byte[] b : labels) {
            hasLabel = b[id] != 0;
            if (hasLabel)
                return true;
        }
        return hasLabel;
    }

    public final byte[] getLabelBits(int[] labelids) {
        byte[] bits = new byte[labels.size()];
        for (int label : labelids)
            bits[label / labelBits] |= 1 << (label % labelBits);

        return bits;
    }

    public final boolean hasLabel(int id, byte[] labelbits) {
        boolean hasLabel = false;
        for (int i = 0; i < labelbits.length; i++) {
            hasLabel = (labels.get(i)[id] & labelbits[i]) != 0;
            if (hasLabel)
                return true;
        }
        return hasLabel;
    }

    public final boolean hasLabel(int id, int label) {
        int p = 1 << (label % labelBits);
        int bit = labels.get(label / labelBits)[id] & p;
        return bit != 0;

    }

    public void removeLabel(List<Integer> ids, int label) {
        int labelOrder = label / labelBits;
        int labelMod = label % labelBits;
        int labelMask = ~(1 << labelMod);
        byte[] labelBytes = labels.get(labelOrder);
        for (int i = 0; i < ids.size(); i++) {
            labelBytes[ids.get(i)] &= labelMask;
        }

    }

    public int newLabel(String labelName) {

        int labelId = getLabelId(labelName);
        if (labelId != -1)
            return labelId;

        if (labelNames.size() > 0)
            for (int i = 0; i <= labelNames.lastKey(); i++)
                if (labelNames.get(i) == null) {
                    labelId = i;
                    break;
                }

        if (labelId == -1 && labelNames.size() % labelBits == 0) {
            byte[] newLabels = new byte[selected.length];
            labels.add(newLabels);
        }
        if (labelId == -1)
            labelId = labelNames.size();

        labelNames.put(labelId, labelName);
        labelComments.put(labelId, null);

        return labelId;
    }

    public void delLabel(int label) {
        if (label == -1)
            return;
        labelNames.remove(label);
        labelComments.remove(label);
        reportLabels.remove(label);

        int labelOrder = label / labelBits;
        int labelMod = label % labelBits;
        int labelMask = ~(1 << labelMod);
        byte[] labelBytes = labels.get(labelOrder);
        for (int i = 0; i < labelBytes.length; i++) {
            labelBytes[i] &= labelMask;
        }
    }

    public void changeLabel(int labelId, String newLabel) {
        if (labelId != -1)
            labelNames.put(labelId, newLabel);
    }

    public int getLabelId(String labelName) {
        for (int i : labelNames.keySet()) {
            if (labelNames.get(i).equals(labelName))
                return i;
        }
        return -1;
    }

    public String getLabelName(int labelId) {
        return labelNames.get(labelId);
    }

    public void setLabelComment(int labelId, String comment) {
        labelComments.put(labelId, comment);
    }

    public String getLabelComment(int labelId) {
        return labelComments.get(labelId);
    }

    public void setInReport(int labelId, boolean inReport) {
        if (inReport)
            reportLabels.add(labelId);
        else
            reportLabels.remove(labelId);
    }

    public boolean isInReport(int labelId) {
        return reportLabels.contains(labelId);
    }

    public LuceneSearchResult filtrarMarcadores(LuceneSearchResult result, Set<String> labelNames, IIPEDSource ipedCase)
            throws Exception {
        result = result.clone();

        int[] labelIds = new int[labelNames.size()];
        int i = 0;
        for (String labelName : labelNames)
            labelIds[i++] = getLabelId(labelName);
        byte[] labelBits = getLabelBits(labelIds);

        for (i = 0; i < result.getLength(); i++)
            if (!hasLabel(ipedCase.getId(result.getLuceneIds()[i]), labelBits)) {
                result.getLuceneIds()[i] = -1;
            }

        result.clearResults();
        return result;
    }

    public LuceneSearchResult filtrarSemEComMarcadores(LuceneSearchResult result, Set<String> labelNames,
            IIPEDSource ipedCase) throws Exception {
        result = result.clone();

        int[] labelIds = new int[labelNames.size()];
        int i = 0;
        for (String labelName : labelNames)
            labelIds[i++] = getLabelId(labelName);
        byte[] labelBits = getLabelBits(labelIds);

        for (i = 0; i < result.getLength(); i++)
            if (hasLabel(ipedCase.getId(result.getLuceneIds()[i]))
                    && !hasLabel(ipedCase.getId(result.getLuceneIds()[i]), labelBits)) {
                result.getLuceneIds()[i] = -1;
            }

        result.clearResults();
        return result;
    }

    public LuceneSearchResult filtrarSemMarcadores(LuceneSearchResult result, IIPEDSource ipedCase) {
        result = result.clone();
        for (int i = 0; i < result.getLength(); i++)
            if (hasLabel(ipedCase.getId(result.getLuceneIds()[i]))) {
                result.getLuceneIds()[i] = -1;
            }

        result.clearResults();
        return result;
    }

    public LuceneSearchResult filtrarSelecionados(LuceneSearchResult result, IIPEDSource ipedCase) throws Exception {
        result = result.clone();
        for (int i = 0; i < result.getLength(); i++)
            if (!selected[ipedCase.getId(result.getLuceneIds()[i])]) {
                result.getLuceneIds()[i] = -1;
            }

        result.clearResults();
        return result;
    }

    public LuceneSearchResult filterInReport(LuceneSearchResult result, IIPEDSource ipedCase) throws Exception {
        result = result.clone();
        for (int i = 0; i < result.getLength(); i++) {
            int itemId = ipedCase.getId(result.getLuceneIds()[i]);
            List<Integer> labels = getLabelIds(itemId);
            boolean inReport = false;
            for (int label : labels)
                if (isInReport(label)) {
                    inReport = true;
                    break;
                }
            if (!inReport)
                result.getLuceneIds()[i] = -1;
        }
        result.clearResults();
        return result;
    }

    public void saveState() {
        try {
            if (stateFile.canWrite() || (!stateFile.exists() && IOUtil.canCreateFile(stateFile.getParentFile())))
                saveState(stateFile);
            else
                saveState(cookie);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveState(File file) throws IOException {
        LOGGER.info("Saving state to file " + file.getAbsolutePath()); //$NON-NLS-1$
        // Util.writeObject(this, file.getAbsolutePath());
        SaveStateThread.getInstance().saveState(this, file);
    }

    public void addToTypedWords(String texto) {

        if (!texto.trim().isEmpty() && !typedWords.contains(texto)) {
            typedWords.add(texto);
            saveState();
        }
    }

    public void loadState() {
        try {
            if (cookie.exists() && (!stateFile.exists() || cookie.lastModified() > stateFile.lastModified()))
                loadState(cookie);

            else if (stateFile.exists())
                loadState(stateFile);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadState(File file) throws IOException, ClassNotFoundException {
        Marcadores state = load(file);

        if (state.selected != null /* && state.read != null */) {
            int len = Math.min(state.selected.length, this.selected.length);
            System.arraycopy(state.selected, 0, this.selected, 0, len);
        }

        this.labels.clear();
        for (byte[] array : state.labels) {
            byte[] newArray = new byte[lastId + 1];
            int len = Math.min(newArray.length, array.length);
            System.arraycopy(array, 0, newArray, 0, len);
            this.labels.add(newArray);
        }

        this.typedWords = state.typedWords;
        this.selectedItens = state.selectedItens;
        this.labelNames = state.labelNames;
        this.labelComments = state.labelComments;
        this.reportLabels = state.reportLabels;
    }

    public static Marcadores load(File file) throws ClassNotFoundException, IOException {
        LOGGER.info("Loading state from file " + file.getAbsolutePath()); //$NON-NLS-1$
        return (Marcadores) Util.readObject(file.getAbsolutePath());
    }

    public void setSelected(boolean value, int id) {
        if (value != selected[id]) {
            if (value)
                selectedItens++;
            else
                selectedItens--;
        }
        // seta valor na versão de visualização ou vice-versa
        selected[id] = value;
    }

}
