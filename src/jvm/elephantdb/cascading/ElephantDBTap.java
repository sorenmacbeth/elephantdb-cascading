package elephantdb.cascading;

import cascading.tuple.Tuple;
import cascading.tuple.TupleEntry;
import elephantdb.DomainSpec;
import elephantdb.hadoop.ElephantRecordWritable;
import elephantdb.persistence.Transmitter;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.mapred.OutputCollector;

import java.io.IOException;


public class ElephantDBTap extends ElephantBaseTap {

    public ElephantDBTap(String dir, Args args) throws IOException {
        this(dir, null, args);
    }

    public ElephantDBTap(String dir) throws IOException {
        this(dir, null, new Args());
    }

    public ElephantDBTap(String dir, DomainSpec spec) throws IOException {
        this(dir, spec, new Args());
    }

    public ElephantDBTap(String dir, DomainSpec spec, Args args) throws IOException {
        super(dir, spec, args);
    }

    @Override public Tuple source(Object key, Object value) {
        key = (_args.deserializer == null) ? key :
            _args.deserializer.deserialize((BytesWritable) key);
        return new Tuple(key, value);
    }

    // This is generic between implementations.
    @Override public void sink(TupleEntry tupleEntry, OutputCollector outputCollector)
        throws IOException {
        int shard = tupleEntry.getInteger(0);
        Object key = tupleEntry.get(1);
        Object val = tupleEntry.get(2);

        Transmitter trans = _fact.getTransmitter();
        byte[] keybytes = trans.serializeKey(key);
        byte[] valuebytes = trans.serializeVal(val);

        ElephantRecordWritable record = new ElephantRecordWritable(keybytes, valuebytes);
        outputCollector.collect(new IntWritable(shard), record);
    }

    // TODO: Implement hashcode and equals in the superclass.
    @Override public int hashCode() {
        return new Integer(_id).hashCode();
    }

    @Override public boolean equals(Object object) {
        if (object instanceof ElephantDBTap) {
            return _id == ((ElephantDBTap) object)._id;
        } else {
            return false;
        }
    }

    private int _id;
    private static int globalid = 0;
}
