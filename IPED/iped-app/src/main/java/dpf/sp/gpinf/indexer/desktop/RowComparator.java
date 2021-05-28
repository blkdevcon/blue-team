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
package dpf.sp.gpinf.indexer.desktop;

import java.io.IOException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedNumericDocValues;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.util.Bits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.config.CategoryLocalization;
import dpf.sp.gpinf.indexer.desktop.TimelineResults.TimeItemId;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.util.Util;
import iped3.IItemId;
import iped3.util.BasicProps;

public class RowComparator implements Comparator<Integer> {

    private static Logger LOGGER = LoggerFactory.getLogger(RowComparator.class);

    private int col;
    private boolean bookmarkCol = false;
    private boolean scoreCol = false;

    private volatile static LeafReader atomicReader;
    private static boolean loadDocValues = true;

    private App app = App.get();

    private String field;
    private HashSet<String> fieldsToLoad = new HashSet<String>();
    private boolean isLongField = false;
    private boolean isDoubleField = false;
    private boolean isTimeStamp = false;
    private boolean isTimeEvent = false;
    private boolean isCategory = false;

    protected SortedDocValues sdv;
    protected SortedSetDocValues ssdv;
    private NumericDocValues ndv;
    private SortedNumericDocValues sndv;
    private int[] localizedCategoryOrds;

    public static void setLoadDocValues(boolean load) {
        loadDocValues = load;
    }

    public RowComparator(int col) {
        this.col = col;

        if (col >= ResultTableModel.fixedCols.length) {
            col -= ResultTableModel.fixedCols.length;
            String[] fields = ResultTableModel.fields;

            if (fields[col].equals(ResultTableModel.BOOKMARK_COL))
                bookmarkCol = true;

            else if (fields[col].equals(ResultTableModel.SCORE_COL))
                scoreCol = true;

            else {
                LOGGER.info("Loading sort data for Column: " + fields[col]); //$NON-NLS-1$
                loadDocValues(fields[col]);
                LOGGER.info("Sort data loaded for Column: " + fields[col]); //$NON-NLS-1$
            }
        }
    }

    public RowComparator(String indexedField) {
        loadDocValues(indexedField);
    }

    private void loadDocValues(String indexedField) {
        field = indexedField;
        fieldsToLoad.add(field);
        String[] fixedNumericFields = { IndexItem.ID, IndexItem.PARENTID, IndexItem.SLEUTHID, IndexItem.LENGTH };
        isLongField = Arrays.asList(fixedNumericFields).contains(field)
                || Integer.class.equals(IndexItem.getMetadataTypes().get(field))
                || Long.class.equals(IndexItem.getMetadataTypes().get(field));
        isDoubleField = Float.class.equals(IndexItem.getMetadataTypes().get(field))
                || Double.class.equals(IndexItem.getMetadataTypes().get(field));

        isTimeStamp = BasicProps.TIMESTAMP.equals(field);
        isTimeEvent = BasicProps.TIME_EVENT.equals(field);
        isCategory = BasicProps.CATEGORY.equals(field);

        if (!loadDocValues)
            return;

        try {
            atomicReader = App.get().appCase.getLeafReader();

            if (IndexItem.getMetadataTypes().get(indexedField) == null
                    || !IndexItem.getMetadataTypes().get(indexedField).equals(String.class)) {
                ndv = atomicReader.getNumericDocValues(indexedField);
                if (ndv == null) {
                    ndv = atomicReader.getNumericDocValues(IndexItem.POSSIBLE_NUM_DOCVALUES_PREFIX + indexedField); // $NON-NLS-1$
                }
                if (ndv == null) {
                    sndv = atomicReader.getSortedNumericDocValues(indexedField);
                    if (sndv == null)
                        sndv = atomicReader
                                .getSortedNumericDocValues(IndexItem.POSSIBLE_NUM_DOCVALUES_PREFIX + indexedField); // $NON-NLS-1$
                }
            }
            if (ndv == null && sndv == null) {
                ssdv = atomicReader.getSortedSetDocValues(indexedField);
                if (ssdv == null)
                    ssdv = atomicReader.getSortedSetDocValues(IndexItem.POSSIBLE_STR_DOCVALUES_PREFIX + indexedField); // $NON-NLS-1$
                if (isCategory) {
                    localizedCategoryOrds = getLocalizedCategoryOrd(ssdv);
                }
            }
            if (ndv == null && sndv == null && ssdv == null) {
                sdv = atomicReader.getSortedDocValues(indexedField);
                if (sdv == null)
                    sdv = atomicReader.getSortedDocValues(IndexItem.POSSIBLE_STR_DOCVALUES_PREFIX + indexedField); // $NON-NLS-1$
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static int[] getLocalizedCategoryOrd(SortedSetDocValues ssdv) {
        int[] localizedOrds = new int[(int) ssdv.getValueCount()];
        ArrayList<String> localizedVals = new ArrayList<>();
        for (int i = 0; i < localizedOrds.length; i++) {
            String val = ssdv.lookupOrd(i).utf8ToString();
            String localizedVal = CategoryLocalization.getInstance().getLocalizedCategory(val);
            localizedVals.add(localizedVal);
        }
        ArrayList<String> sortedLocalizedVals = new ArrayList<>(localizedVals);
        Collator collator = Collator.getInstance();
        collator.setStrength(Collator.PRIMARY);
        Collections.sort(sortedLocalizedVals, collator);
        for(int i = 0; i < localizedOrds.length; i++) {
            localizedOrds[i] = sortedLocalizedVals.indexOf(localizedVals.get(i));
        }
        return localizedOrds;
    }

    private ThreadLocal<Bits> localDocsWithField = new ThreadLocal<Bits>() {
        @Override
        protected Bits initialValue() {
            try {
                Bits bits = atomicReader.getDocsWithField(field);
                if (bits == null)
                    bits = atomicReader.getDocsWithField(IndexItem.POSSIBLE_NUM_DOCVALUES_PREFIX + field); // $NON-NLS-1$
                return bits;

            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    };

    private ThreadLocal<NumericDocValues> localNDV = new ThreadLocal<NumericDocValues>() {
        @Override
        protected NumericDocValues initialValue() {
            try {
                NumericDocValues ndv = atomicReader.getNumericDocValues(field);
                if (ndv == null)
                    ndv = atomicReader.getNumericDocValues(IndexItem.POSSIBLE_NUM_DOCVALUES_PREFIX + field); // $NON-NLS-1$
                return ndv;

            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    };

    private ThreadLocal<SortedDocValues> localSDV = new ThreadLocal<SortedDocValues>() {
        @Override
        protected SortedDocValues initialValue() {
            try {
                SortedDocValues sdv = atomicReader.getSortedDocValues(field);
                if (sdv == null)
                    sdv = atomicReader.getSortedDocValues(IndexItem.POSSIBLE_STR_DOCVALUES_PREFIX + field);
                return sdv;

            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    };

    private ThreadLocal<SortedSetDocValues> localSSDV = new ThreadLocal<SortedSetDocValues>() {
        @Override
        protected SortedSetDocValues initialValue() {
            try {
                SortedSetDocValues ssdv = atomicReader.getSortedSetDocValues(field);
                if (ssdv == null)
                    ssdv = atomicReader.getSortedSetDocValues(IndexItem.POSSIBLE_STR_DOCVALUES_PREFIX + field);
                return ssdv;

            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    };

    private ThreadLocal<SortedNumericDocValues> localSNDV = new ThreadLocal<SortedNumericDocValues>() {
        @Override
        protected SortedNumericDocValues initialValue() {
            try {
                SortedNumericDocValues sndv = atomicReader.getSortedNumericDocValues(field);
                if (sndv == null)
                    sndv = atomicReader.getSortedNumericDocValues(IndexItem.POSSIBLE_NUM_DOCVALUES_PREFIX + field); // $NON-NLS-1$
                return sndv;

            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    };

    public static boolean isNewIndexReader() {
        return atomicReader != App.get().appCase.getLeafReader();
    }

    public boolean isStringComparator() {
        return sdv != null || ssdv != null || bookmarkCol;
    }

    @Override
    public final int compare(Integer a, Integer b) {

        if (Thread.currentThread().isInterrupted())
            throw new RuntimeException(Messages.getString("RowComparator.SortCanceled")); //$NON-NLS-1$

        if (scoreCol)
            return Float.compare(app.ipedResult.getScore(a), app.ipedResult.getScore(b));

        IItemId itemA = app.ipedResult.getItem(a);
        IItemId itemB = app.ipedResult.getItem(b);

        a = app.appCase.getLuceneId(itemA);
        b = app.appCase.getLuceneId(itemB);

        if (col == 1) {
            if (app.appCase.getMultiMarcadores().isSelected(itemA) == app.appCase.getMultiMarcadores()
                    .isSelected(itemB))
                return 0;
            else if (app.appCase.getMultiMarcadores().isSelected(itemA) == true)
                return -1;
            else
                return 1;

        } else if (bookmarkCol) {
            return Util.concatStrings(app.appCase.getMultiMarcadores().getLabelList(itemA))
                    .compareTo(Util.concatStrings(app.appCase.getMultiMarcadores().getLabelList(itemB)));

        } else if (isTimeStamp && itemA instanceof TimeItemId) {
            int ordA = ((TimeItemId) itemA).getTimeStampOrd();
            int ordB = ((TimeItemId) itemB).getTimeStampOrd();
            return Integer.compare(ordA, ordB);

        } else if (isTimeEvent && itemA instanceof TimeItemId) {
            int ordA = ((TimeItemId) itemA).getTimeEventOrd();
            int ordB = ((TimeItemId) itemB).getTimeEventOrd();
            return Integer.compare(ordA, ordB);

        } else if (sdv != null) {
            SortedDocValues sdv = localSDV.get();
            return sdv.getOrd(a) - sdv.getOrd(b);

        } else if (ssdv != null) {
            SortedSetDocValues lssdv = localSSDV.get();
            int result, k = 0, ordA = -1, ordB = -1;
            do {
                int i = 0;
                lssdv.setDocument(a);
                while (i++ <= k)
                    ordA = (int) lssdv.nextOrd();
                i = 0;
                lssdv.setDocument(b);
                while (i++ <= k)
                    ordB = (int) lssdv.nextOrd();
                if (isCategory) {
                    if (ordA > -1) {
                        ordA = localizedCategoryOrds[ordA];
                    }
                    if (ordB > -1) {
                        ordB = localizedCategoryOrds[ordB];
                    }
                }
                result = ordA - ordB;
                k++;

            } while (result == 0 && ordA != SortedSetDocValues.NO_MORE_ORDS && ordB != SortedSetDocValues.NO_MORE_ORDS);

            return result;

        } else if (sndv != null) {
            SortedNumericDocValues lsndv = localSNDV.get();
            int result, k = 0, countA = 0, countB = 0;
            do {
                long ordA, ordB;
                lsndv.setDocument(a);
                if (k == 0)
                    countA = lsndv.count();
                if (k < countA)
                    ordA = lsndv.valueAt(k);
                else
                    ordA = Long.MIN_VALUE;
                lsndv.setDocument(b);
                if (k == 0)
                    countB = lsndv.count();
                if (k < countB)
                    ordB = lsndv.valueAt(k);
                else
                    ordB = Long.MIN_VALUE;
                result = Long.compare(ordA, ordB);
                k++;

            } while (result == 0 && (k < countA || k < countB));

            return result;

        } else if (ndv != null) {
            Bits docsWithField = localDocsWithField.get();
            if (docsWithField.get(a)) {
                if (docsWithField.get(b)) {
                    NumericDocValues ndv = localNDV.get();
                    return Long.compare(ndv.get(a), ndv.get(b));
                } else
                    return 1;
            } else if (docsWithField.get(b))
                return -1;
            else
                return 0;

        }

        // On demand sorting if DocValues does not exist for this field (much slower)
        try {
            Document doc1 = app.appCase.getReader().document(a, fieldsToLoad);
            Document doc2 = app.appCase.getReader().document(b, fieldsToLoad);

            String v1 = doc1.get(field);
            String v2 = doc2.get(field);

            if (v1 == null || v1.isEmpty()) {
                if (v2 == null || v2.isEmpty())
                    return 0;
                else
                    return -1;
            } else if (v2 == null || v2.isEmpty())
                return 1;

            if (isLongField) {
                long l1 = Long.parseLong(v1);
                long l2 = Long.parseLong(v2);
                return Long.compare(l1, l2);
            }
            if (isDoubleField) {
                double d1 = Double.parseDouble(v1);
                double d2 = Double.parseDouble(v2);
                return Double.compare(d1, d2);
            }

            return v1.compareTo(v2);

        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }

    }

}
