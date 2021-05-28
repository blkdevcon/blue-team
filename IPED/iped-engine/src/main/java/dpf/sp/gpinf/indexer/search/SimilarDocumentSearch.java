package dpf.sp.gpinf.indexer.search;

import java.io.IOException;

import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.br.BrazilianAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.pt.PortugueseAnalyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.mlt.MoreLikeThis;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

import dpf.sp.gpinf.indexer.process.IndexItem;
import iped3.IIPEDSource;
import iped3.IItemId;

public class SimilarDocumentSearch {

    private static final CharArraySet stopSet = getStopWords();

    public Query getQueryForSimilarDocs(IItemId item, int matchPercent, IIPEDSource appCase) {

        int docId = appCase.getLuceneId(item);

        MoreLikeThis mlt = new MoreLikeThis(appCase.getReader());
        String[] fields = { IndexItem.CONTENT };

        mlt.setMaxQueryTerms(50);
        mlt.setFieldNames(fields);
        mlt.setAnalyzer(appCase.getAnalyzer());
        mlt.setBoost(true);
        mlt.setMinDocFreq(2);
        mlt.setMaxDocFreqPct(10);
        mlt.setMinTermFreq(1);
        mlt.setMaxNumTokensParsed(10000);
        mlt.setMinWordLen(4);
        mlt.setMaxWordLen(25);
        mlt.setStopWords(stopSet);
        try {
            /*
             * IndexerDefaultParser autoParser = new IndexerDefaultParser();
             * autoParser.setFallback(Configuration.fallBackParser);
             * autoParser.setErrorParser(Configuration.errorParser);
             * autoParser.setPrintMetadata(false);
             * 
             * EvidenceFile ev = App.get().appCase.getItemByItemId(item); Metadata m = new
             * Metadata(); m.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE,
             * ev.getMediaType().toString());
             * 
             * ParsingReader pr = new ParsingReader(autoParser, ev.getStream(), m, new
             * ParseContext()); pr.startBackgroundParsing();
             * 
             * String[] keyTerms = mlt.retrieveInterestingTerms(pr, IndexItem.CONTENT);
             */

            // Funciona apenas com term vectors
            String[] keyTerms = mlt.retrieveInterestingTerms(docId);

            BooleanQuery.Builder query = new BooleanQuery.Builder();
            System.out.print(keyTerms.length + ": "); //$NON-NLS-1$

            for (String s : keyTerms) {
                query.add(new TermQuery(new Term(IndexItem.CONTENT, s)), Occur.SHOULD);
                System.out.print(s + " "); //$NON-NLS-1$
            }
            System.out.println();

            query.setMinimumNumberShouldMatch(keyTerms.length * matchPercent / 100);

            return query.build();

        } catch (IOException e1) {
            e1.printStackTrace();
        }
        return null;
    }

    private static CharArraySet getStopWords() {
        CharArraySet stopSet = BrazilianAnalyzer.getDefaultStopSet();
        stopSet.addAll(PortugueseAnalyzer.getDefaultStopSet());
        stopSet.addAll(EnglishAnalyzer.getDefaultStopSet());
        return stopSet;
    }

}
