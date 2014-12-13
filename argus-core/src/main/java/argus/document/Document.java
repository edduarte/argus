package argus.document;

import argus.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Simple structure that holds a document current snapshot and associates
 * it with an url.
 *
 * @author Eduardo Duarte (<a href="mailto:eduardo.miguel.duarte@gmail.com">eduardo.miguel.duarte@gmail.com</a>)
 * @version 1.0
 */
public final class Document extends BasicDBObject implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final int BOUND_INDEX = 4;

    public static final String ID = "id";
    public static final String URL = "url";
    public static final String ORIGINAL_CONTENT = "original_content";

    private transient final DBCollection occCollection;

    Document(DB occurrencesDB, String url, String originalContent) {
        super(ID, Constants.bytesToHex(Constants.generateRandomBytes()));
        append(URL, url);
        append(ORIGINAL_CONTENT, originalContent);
        occCollection = occurrencesDB
                .getCollection(getUrl().hashCode() + getString(ID));
    }

    Document(DB occurrencesDB, BasicDBObject dbObject) {
        super(dbObject);
        occCollection = occurrencesDB
                .getCollection(getUrl().hashCode() + getString(ID));
    }

    public void addOccurrence(Occurrence occurrence) {
        occCollection.insert(occurrence);
    }

    public void addOccurrences(Iterable<Occurrence> occurrencess) {
        addOccurrences(StreamSupport.stream(occurrencess.spliterator(), false));
    }

    public void addOccurrences(Stream<Occurrence> occurrencesStream) {
        BulkWriteOperation builder = occCollection.initializeUnorderedBulkOperation();
        occurrencesStream.forEach(builder::insert);
        builder.execute();
        builder = null;
    }

    public Occurrence getOccurrence(String text, int wordCount) {
        if (text.isEmpty()) {
            return null;
        }
        int lowerBound = wordCount - BOUND_INDEX;
        int upperBound = wordCount + BOUND_INDEX;

        BasicDBObject boundQuery =
                new BasicDBObject("$gt", lowerBound).append("$lt", upperBound);
        BasicDBObject queriedObject = (BasicDBObject) occCollection.findOne(
                new BasicDBObject(Occurrence.TEXT, text).append(Occurrence.WORD_COUNT, boundQuery));
        return queriedObject != null ? new Occurrence(queriedObject) : null;
    }

    public List<Occurrence> getAllOccurrences(String occurrencesText) {
        if (occurrencesText.isEmpty()) {
            return null;
        }
        DBCursor cursor = occCollection.find(new BasicDBObject(Occurrence.TEXT, occurrencesText));
        List<Occurrence> list = new ArrayList<>();
        while (cursor.hasNext()) {
            BasicDBObject obj = (BasicDBObject) cursor.next();
            list.add(new Occurrence(obj));
        }
        cursor.close();
        return list;
    }

    public String getUrl() {
        return getString(URL);
    }

    public String getOriginalContent() {
        return getString(ORIGINAL_CONTENT);
    }

    /**
     * Converts a cluster of occurrences associated with a document into a String,
     * where each occurrences is separated by a whitespace.
     */
    @SuppressWarnings("unchecked")
    public String getProcessedContent() {
        DBCursor cursor = occCollection.find();
        return StreamSupport.stream(cursor.spliterator(), false)
                .map(Occurrence::new)
                .map(Occurrence::toString)
                .collect(Collectors.joining(" "));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        Document that = (Document) o;
        return this.getUrl().equalsIgnoreCase(that.getUrl());
    }

    @Override
    public int hashCode() {
        return getUrl().hashCode();
    }

    @Override
    public String toString() {
        return getUrl();
    }

    void destroy() {
        occCollection.drop();
    }
}

