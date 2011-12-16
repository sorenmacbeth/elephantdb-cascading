package elephantdb.cascading;

import cascading.tuple.Tuple;
import elephantdb.persistence.Document;

/** User: sritchie Date: 12/16/11 Time: 12:12 AM */
public class IdentityGateway implements IGateway {

    public Object buildDocument(Tuple tuple) {
        return tuple.getObject(1);
    }

    public Tuple buildTuple(Object obj) {
        return new Tuple(obj);
    }
}