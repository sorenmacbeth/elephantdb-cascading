package elephantdb.cascading;

import cascading.flow.FlowProcess;
import cascading.operation.BaseOperation;
import cascading.operation.Function;
import cascading.operation.FunctionCall;
import cascading.operation.Identity;
import cascading.pipe.Each;
import cascading.pipe.GroupBy;
import cascading.pipe.Pipe;
import cascading.pipe.SubAssembly;
import cascading.tuple.Fields;
import cascading.tuple.Tuple;
import elephantdb.DomainSpec;
import elephantdb.persistence.KryoWrapper;
import org.apache.hadoop.io.BytesWritable;
import org.apache.log4j.Logger;

import java.util.UUID;

public class ElephantTailAssembly extends SubAssembly {
    public static Logger LOG = Logger.getLogger(ElephantTailAssembly.class);

    public static class Shardize extends BaseOperation implements Function {
        DomainSpec _spec;

        public Shardize(String outfield, DomainSpec spec) {
            super(new Fields(outfield));
            _spec = spec;
        }

        public void operate(FlowProcess process, FunctionCall call) {
            Object key = call.getArguments().getObject(0);
            int shard = _spec.shardIndex(key);
            call.getOutputCollector().add(new Tuple(shard));
        }
    }

    public static class MakeSortableKey extends BaseOperation implements Function {
        DomainSpec _spec;
        KryoWrapper.KryoBuffer _kryoBuf;

        public MakeSortableKey(String outfield, DomainSpec spec) {
            super(new Fields(outfield));
            _spec = spec;
            _kryoBuf = _spec.getCoordinator().getKryoBuffer();
        }

        public void operate(FlowProcess process, FunctionCall call) {
            Object key = call.getArguments().getObject(0);
            BytesWritable sortField = new BytesWritable(_kryoBuf.serialize(key));
            call.getOutputCollector().add(new Tuple(sortField));
        }
    }

    public ElephantTailAssembly(Pipe keyValuePairs, ElephantDBTap outTap) {
        // generate two random field names
        String shardField = "shard" + UUID.randomUUID().toString();
        String keySortField = "keysort" + UUID.randomUUID().toString();

        DomainSpec spec = outTap.getSpec();
        LOG.info("Instantiating spec: " + spec);

        // Add the shard index as field #2.
        Pipe out =
            new Each(keyValuePairs, new Fields(0), new Shardize(shardField, spec), Fields.ALL);

        // Add the serialized key itself as field #3 for sorting.
        // TODO: Make secondary sorting optional, and come up with a function to generate
        // a sortable key (vs just using the same serialization as for sharding).
        out = new Each(out, new Fields(0), new MakeSortableKey(keySortField, spec), Fields.ALL);

        //put in order of shard, key, value, sortablekey
        out = new Each(out, new Fields(2, 0, 1, 3), new Identity(), Fields.RESULTS);
        out = new GroupBy(out, new Fields(0), new Fields(3)); // group by shard

        // emit shard, key, value
        out = new Each(out, new Fields(0, 1, 2), new Identity());
        setTails(out);
    }
}
